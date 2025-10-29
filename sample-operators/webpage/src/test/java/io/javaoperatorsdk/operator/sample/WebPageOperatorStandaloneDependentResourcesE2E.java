/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class WebPageOperatorStandaloneDependentResourcesE2E extends WebPageOperatorAbstractTest {

  public WebPageOperatorStandaloneDependentResourcesE2E() throws FileNotFoundException {}

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withReconciler(new WebPageStandaloneDependentsReconciler())
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).items())
              .build();

  @Override
  AbstractOperatorExtension operator() {
    return operator;
  }
}
