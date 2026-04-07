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
package io.javaoperatorsdk.operator.workflow.onedependentactivationcondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OneDependentActivationConditionIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(OneDependentActivationConditionReconciler.class)
          .build();

  @Test
  void handlesOnlyNonActiveDependent() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      extension
                          .getReconcilerOfType(OneDependentActivationConditionReconciler.class)
                          .getNumberOfReconciliationExecution())
                  .isPositive();
            });

    // ConfigMap should not be created since the activation condition is false
    var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
    assertThat(cm).isNull();
  }

  private OneDependentActivationConditionCustomResource testResource() {
    var res = new OneDependentActivationConditionCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
