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
package io.javaoperatorsdk.operator.baseapi.fieldselector;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.baseapi.fieldselector.FieldSelectorTestReconciler.MY_SECRET_TYPE;
import static io.javaoperatorsdk.operator.baseapi.fieldselector.FieldSelectorTestReconciler.OTHER_SECRET_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FieldSelectorIT {

  public static final String TEST_1 = "test1";
  public static final String TEST_2 = "test2";
  public static final String TEST_3 = "test3";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new FieldSelectorTestReconciler())
          .build();

  @Test
  void filtersCustomResourceByLabel() {

    var customPrimarySecret =
        extension.create(
            new SecretBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(TEST_1).build())
                .withType(MY_SECRET_TYPE)
                .build());

    var otherSecret =
        extension.create(
            new SecretBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(TEST_2).build())
                .build());

    var dependentSecret =
        extension.create(
            new SecretBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(TEST_3).build())
                .withType(OTHER_SECRET_TYPE)
                .build());

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              var r = extension.getReconcilerOfType(FieldSelectorTestReconciler.class);
              assertThat(r.getReconciledSecrets()).containsExactly(TEST_1);

              assertThat(
                      r.getDependentSecretEventSource()
                          .get(ResourceID.fromResource(dependentSecret)))
                  .isPresent();
              assertThat(
                      r.getDependentSecretEventSource()
                          .get(ResourceID.fromResource(customPrimarySecret)))
                  .isNotPresent();
              assertThat(
                      r.getDependentSecretEventSource().get(ResourceID.fromResource(otherSecret)))
                  .isNotPresent();
            });
  }
}
