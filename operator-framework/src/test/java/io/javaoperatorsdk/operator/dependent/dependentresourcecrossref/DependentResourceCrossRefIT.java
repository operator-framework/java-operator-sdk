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
package io.javaoperatorsdk.operator.dependent.dependentresourcecrossref;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentResourceCrossRefIT {

  public static final String TEST_RESOURCE_NAME = "test";
  public static final int EXECUTION_NUMBER = 50;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new DependentResourceCrossRefReconciler())
          .build();

  @Test
  void dependentResourceCanReferenceEachOther() {

    for (int i = 0; i < EXECUTION_NUMBER; i++) {
      operator.create(testResource(i));
    }
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(DependentResourceCrossRefReconciler.class)
                          .isErrorHappened())
                  .isFalse();
              for (int i = 0; i < EXECUTION_NUMBER; i++) {
                assertThat(operator.get(ConfigMap.class, TEST_RESOURCE_NAME + i)).isNotNull();
                assertThat(operator.get(Secret.class, TEST_RESOURCE_NAME + i)).isNotNull();
              }
            });

    for (int i = 0; i < EXECUTION_NUMBER; i++) {
      operator.delete(testResource(i));
    }
    await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              for (int i = 0; i < EXECUTION_NUMBER; i++) {
                assertThat(
                        operator.get(
                            DependentResourceCrossRefResource.class,
                            testResource(i).getMetadata().getName()))
                    .isNull();
              }
            });
  }

  DependentResourceCrossRefResource testResource(int n) {
    var res = new DependentResourceCrossRefResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME + n).build());
    return res;
  }
}
