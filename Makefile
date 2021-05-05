# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Include the main common Makefile containing
# basic common recipes and vars
#
include config/common/Makefile

#
# Override relative directories from common Makefile
#
PKG := ./pkg
CRD := ./config/crd/bases
SCRIPT := ./config/script

SNAPSHOT_VERSION := $(VERSION)-SNAPSHOT
LAST_RELEASED_VERSION := 0.2.0
RELEASE_GIT_REMOTE := upstream
GIT_COMMIT := $(shell git rev-list -1 HEAD)

# OLM (Operator Lifecycle Manager and Operator Hub): uncomment to override operator settings at build time
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultOperatorName=yaks-operator
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultPackage=yaks
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultChannel=alpha
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultSource=community-operators
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultSourceNamespace=openshift-marketplace
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultStartingCSV=
#GOLDFLAGS += -X github.com/citrusframework/yaks/pkg/util/olm.DefaultGlobalNamespace=openshift-operators

GOLDFLAGS += -X main.GitCommit=$(GIT_COMMIT)
GOFLAGS = -ldflags "$(GOLDFLAGS)" -gcflags=-trimpath=$(GO_PATH) -asmflags=-trimpath=$(GO_PATH)

.DEFAULT_GOAL := default

default: test

clean:
	./script/clean.sh

check-licenses:
	./script/check_licenses.sh

check-repo:
	./script/check_repo.sh

#
# Will override the generate located in config/Makefile
# to include the generate-client rule
#
generate: generate-deepcopy generate-crds generate-client

generate-client:
	./script/gen_client.sh

build: build-resources build-yaks

test: build
	go test ./...

build-yaks:
	go build $(GOFLAGS) -o yaks ./cmd/manager/*.go

build-resources:
	go generate ./pkg/...

set-version-file:
	./script/set_version_file.sh $(VERSION) $(SNAPSHOT_VERSION) $(IMAGE_NAME)

set-version:
	./script/set_version.sh $(VERSION) $(SNAPSHOT_VERSION) $(IMAGE_NAME)

set-next-version:
	./script/set_next_version.sh --snapshot-version $(SNAPSHOT_VERSION)

cross-compile:
	./script/cross_compile.sh $(VERSION) '$(GOFLAGS)'

docker-build: package-artifacts-no-test
	./script/docker-build.sh $(IMAGE_NAME):$(VERSION) '$(GOFLAGS)'

images-no-test: build package-artifacts-no-test docker-build

images: test package-artifacts docker-build

images-push:
	docker push $(IMAGE_NAME):$(VERSION)

prepare-release: check-repo clean check-licenses

release-dry-run: prepare-release
	./script/release.sh --release-version $(VERSION) --snapshot-version $(SNAPSHOT_VERSION) --image $(IMAGE_NAME) --go-flags '$(GOFLAGS)' --git-remote $(RELEASE_GIT_REMOTE) --skip-tests --dry-run --no-git-push --keep-staging-repo

release: prepare-release
	./script/release.sh --release-version $(VERSION) --snapshot-version $(SNAPSHOT_VERSION) --image $(IMAGE_NAME) --go-flags '$(GOFLAGS)' --git-remote $(RELEASE_GIT_REMOTE) --skip-tests

release-local: prepare-release
	./script/release.sh --release-version $(VERSION) --snapshot-version $(SNAPSHOT_VERSION) --image $(IMAGE_NAME) --go-flags '$(GOFLAGS)' --git-remote $(RELEASE_GIT_REMOTE) --local-release --no-git-push --no-docker-push

release-major: prepare-release
	./script/release.sh --release-version $(VERSION) --snapshot-version $(SNAPSHOT_VERSION) --image $(IMAGE_NAME) --go-flags '$(GOFLAGS)' --git-remote $(RELEASE_GIT_REMOTE) --skip-tests --major-release

release-snapshot: prepare-release
	./script/release.sh --snapshot-release --snapshot-version $(SNAPSHOT_VERSION) --image $(IMAGE_NAME) --go-flags '$(GOFLAGS)' --git-remote $(RELEASE_GIT_REMOTE) --no-git-push

release-nightly: prepare-release
	./script/release.sh --snapshot-release --release-version $(VERSION) --snapshot-version $(SNAPSHOT_VERSION) --image $(IMAGE_NAME) --go-flags '$(GOFLAGS)' --git-remote $(RELEASE_GIT_REMOTE) --no-git-push

generate-docs:
	./script/docs.sh docs --release-version $(VERSION) --local --html-only

release-docs:
	./script/docs.sh docs --release-version $(VERSION)

release-docs-major:
	./script/docs.sh docs --release-version $(VERSION) --major-release

release-docs-dry-run:
	./script/docs.sh docs --release-version $(VERSION) --dry-run

package-artifacts-no-test:
	./script/package_maven_artifacts.sh --release-version $(VERSION) --local-release --skip-tests

package-artifacts:
	./script/package_maven_artifacts.sh --release-version $(VERSION) --local-release

snapshot-version:
	@echo $(SNAPSHOT_VERSION)

version:
	@echo $(VERSION)

.PHONY: clean build build-yaks build-resources generate-docs release-docs release-docs-dry-run release-docs-major update-olm cross-compile test docker-build images images-no-test images-push package-artifacts package-artifacts-no-test release release-snapshot set-version-file set-version set-next-version check-repo check-licenses snapshot-version version
