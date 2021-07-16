// Copyright 2021 The Operator-SDK Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package v1

import (
	"os"
	"path/filepath"
	"strings"

	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/spf13/pflag"

	"sigs.k8s.io/kubebuilder/v3/pkg/config"
	"sigs.k8s.io/kubebuilder/v3/pkg/plugin"
)

var _ = Describe("v1", func() {
	var (
		successInitSubcommand initSubcommand
		failureInitSubcommand initSubcommand
	)

	BeforeEach(func() {
		successInitSubcommand = initSubcommand{
			domain: "testDomain",
		}

		failureInitSubcommand = initSubcommand{
			domain:      "testDomain",
			projectName: "?&fail&?",
			commandName: "failureTest",
		}
	})

	Describe("UpdateMetadata", func() {
		It("Check that function call sets data correctly", func() {
			testCliMetadata := plugin.CLIMetadata{CommandName: "TestCommand"}
			testSubcommandMetadata := plugin.SubcommandMetadata{}
			Expect(failureInitSubcommand.commandName).NotTo(Equal(testCliMetadata.CommandName))

			successInitSubcommand.UpdateMetadata(testCliMetadata, &testSubcommandMetadata)
			Expect(successInitSubcommand.commandName).To(Equal(testCliMetadata.CommandName))
		})
	})

	Describe("BindFlags", func() {
		It("verify all fields were set correctly", func() {
			flagTest := pflag.NewFlagSet("testFlag", -1)
			successInitSubcommand.BindFlags(flagTest)
			Expect(flagTest.SortFlags).To(BeFalse())
			Expect(successInitSubcommand.domain).To(Equal("my.domain"))
			Expect(successInitSubcommand.projectName).To(Equal(""))
			Expect(successInitSubcommand.group).To(Equal(""))
			Expect(successInitSubcommand.version).To(Equal(""))
			Expect(successInitSubcommand.kind).To(Equal(""))
		})
	})

	Describe("InjectConfig", func() {
		It("verify all fields were set correctly", func() {
			testConfig, _ := config.New(config.Version{Number: 3})
			dir, _ := os.Getwd()
			Expect(failureInitSubcommand.InjectConfig(testConfig)).To(HaveOccurred())

			successInitSubcommand.InjectConfig(testConfig)
			Expect(successInitSubcommand.config, testConfig)
			Expect(successInitSubcommand.domain, testConfig.GetDomain())
			Expect(successInitSubcommand.projectName, strings.ToLower(filepath.Base(dir)))
			Expect(successInitSubcommand.projectName, testConfig.GetProjectName())
			Expect(successInitSubcommand.InjectConfig(testConfig)).To(BeNil())
		})
	})

	Describe("Validate", func() {
		It("should return nil", func() {
			Expect(successInitSubcommand.Validate()).To(BeNil())
		})
	})

	Describe("PostScaffold", func() {
		It("should return nil", func() {
			Expect(successInitSubcommand.PostScaffold()).To(BeNil())
		})
	})
})
