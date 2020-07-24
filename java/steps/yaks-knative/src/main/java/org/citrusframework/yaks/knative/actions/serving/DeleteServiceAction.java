/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citrusframework.yaks.knative.actions.serving;

import com.consol.citrus.context.TestContext;
import org.citrusframework.yaks.knative.actions.AbstractKnativeAction;

/**
 * @author Christoph Deppisch
 */
public class DeleteServiceAction extends AbstractKnativeAction {

    private final String serviceName;

    public DeleteServiceAction(Builder builder) {
        super("delete-service", builder);

        this.serviceName = builder.serviceName;
    }

    @Override
    public void doExecute(TestContext context) {
        getKubernetesClient().services().inNamespace(namespace(context))
                .withName(serviceName)
                .delete();
    }

    /**
     * Action builder.
     */
    public static class Builder extends AbstractKnativeAction.Builder<DeleteServiceAction, Builder> {

        private String serviceName;

        public Builder name(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public DeleteServiceAction build() {
            return new DeleteServiceAction(this);
        }
    }
}
