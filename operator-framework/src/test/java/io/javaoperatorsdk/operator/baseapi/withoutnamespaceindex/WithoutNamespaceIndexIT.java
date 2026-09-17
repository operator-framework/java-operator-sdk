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
package io.javaoperatorsdk.operator.baseapi.withoutnamespaceindex;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WithoutNamespaceIndexIT {

  private static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new WithoutNamespaceIndexTestReconciler())
          .build();

  @Test
  void reconcilesAndResolvesSecondariesWithoutTheNamespaceIndex() {
    var configMap =
        extension.create(
            new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build())
                .withData(Map.of("key", "value"))
                .build());
    extension.create(
        new SecretBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build())
            .build());

    var reconciler = extension.getReconcilerOfType(WithoutNamespaceIndexTestReconciler.class);
    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isPositive();
              assertThat(reconciler.getSecondariesFound()).contains(TEST_RESOURCE_NAME);
            });

    // an update has to reach the reconciler too: the informer keeps serving events and cache reads
    // with the index gone, it is not just the initial sync that works
    var executionsBeforeUpdate = reconciler.getNumberOfExecutions();
    configMap.setData(Map.of("key", "updated"));
    extension.update(configMap);

    await()
        .untilAsserted(
            () ->
                assertThat(reconciler.getNumberOfExecutions())
                    .isGreaterThan(executionsBeforeUpdate));
  }
}
