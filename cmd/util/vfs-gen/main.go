/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package main

import (
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strings"
	"time"

	"github.com/citrusframework/yaks/pkg/base"
	"github.com/shurcooL/httpfs/filter"
	"github.com/shurcooL/vfsgen"
)

func main() {

	var rootDir string
	var destDir string

	wd, err := os.Getwd()
	if err != nil {
		log.Fatalln(err)
		os.Exit(1)
	}

	flag.StringVar(&rootDir, "root", base.GoModDirectory, "The absolute path from were the directories can be found (yaks module directory by default)")
	flag.StringVar(&destDir, "dest", wd, "The destination directory of the generated file (working directory by default)")
	flag.Parse()

	if len(flag.Args()) < 1 {
		println("usage: vfs-gen [-root <absolute root parent path>] [-dest <directory>] directory1 [directory2 ... ...]")
		os.Exit(1)
	}

	err = checkDir(rootDir)
	if err != nil {
		log.Fatalln(err)
		os.Exit(1)
	}

	dirName := flag.Args()[0]
	absDir := filepath.Join(rootDir, dirName)
	err = checkDir(absDir)
	if err != nil {
		log.Fatalln(err)
		os.Exit(1)
	}

	exclusions := calcExclusions(rootDir, []string{dirName})

	//
	// Filter un-interesting files
	//
	var fs http.FileSystem = modTimeFS{
		fs: http.Dir(absDir),
	}
	fs = filter.Skip(fs, filter.FilesWithExtensions(".go"))
	fs = filter.Skip(fs, NamedFilesFilter("kustomization.yaml", "Makefile", "Dockerfile", ".gitignore", "PROJECT"))
	fs = filter.Skip(fs, IgnoreParentDirFilter("/bundle", "/operator/patches"))
	fs = filter.Skip(fs, func(path string, fi os.FileInfo) bool {
		for _, ex := range exclusions {
			if strings.HasPrefix(path, ex) {
				return true
			}
		}
		return false
	})

	//
	// Destination file for the generated resources
	//
	resourceFile := path.Join(destDir, "resources.go")
	err = vfsgen.Generate(fs, vfsgen.Options{
		Filename:    resourceFile,
		PackageName: path.Base(destDir),
	})
	if err != nil {
		log.Fatalln(err)
	}

	header := `/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

`
	content, err := ioutil.ReadFile(resourceFile)
	if err != nil {
		log.Fatalln(err)
	}
	var finalContent []byte
	finalContent = append(finalContent, []byte(header)...)
	finalContent = append(finalContent, content...)
	if err := ioutil.WriteFile(resourceFile, finalContent, 0777); err != nil {
		log.Fatalln(err)
	}

}

func NamedFilesFilter(names ...string) func(path string, fi os.FileInfo) bool {
	return func(path string, fi os.FileInfo) bool {
		if fi.IsDir() {
			return false
		}

		for _, name := range names {
			if name == filepath.Base(path) {
				return true
			}
		}

		return false
	}
}

func IgnoreParentDirFilter(parentDirs ...string) func(path string, fi os.FileInfo) bool {
	return func(path string, fi os.FileInfo) bool {
		for _, parentDir := range parentDirs {
			if ok := strings.HasPrefix(path, parentDir); ok {
				return true
			}
		}

		return false
	}
}

func calcExclusions(root string, dirNames []string) []string {
	var exclusions []string

	for _, dirName := range dirNames {
		dirName = filepath.Join(root, dirName)
		if err := filepath.Walk(dirName, func(resPath string, info os.FileInfo, err error) error {
			if info.IsDir() {
				ignoreFileName := path.Join(resPath, ".vfsignore")
				_, err := os.Stat(ignoreFileName)
				if err == nil {
					rel, err := filepath.Rel(dirName, resPath)
					if err != nil {
						log.Fatalln(err)
					}
					if !strings.HasPrefix(rel, "/") {
						rel = "/" + rel
					}
					exclusions = append(exclusions, rel)
				} else if !os.IsNotExist(err) {
					log.Fatalln(err)
				}
			}
			return nil
		}); err != nil {
			log.Fatalln(err)
		}
	}

	return exclusions
}

func checkDir(dirName string) error {
	dir, err := os.Stat(dirName)
	if err != nil {
		return err
	}
	if !dir.IsDir() {
		return fmt.Errorf("path %s is not a directory\n", dirName)
	}

	return nil
}

// modTimeFS wraps http.FileSystem to set mod time to 0 for all files
type modTimeFS struct {
	fs http.FileSystem
}

func (fs modTimeFS) Open(name string) (http.File, error) {
	f, err := fs.fs.Open(name)
	if err != nil {
		return nil, err
	}
	return modTimeFile{f}, nil
}

type modTimeFile struct {
	http.File
}

func (f modTimeFile) Stat() (os.FileInfo, error) {
	fi, err := f.File.Stat()
	if err != nil {
		return nil, err
	}
	return modTimeFileInfo{fi}, nil
}

type modTimeFileInfo struct {
	os.FileInfo
}

func (modTimeFileInfo) ModTime() time.Time {
	return time.Time{}
}
