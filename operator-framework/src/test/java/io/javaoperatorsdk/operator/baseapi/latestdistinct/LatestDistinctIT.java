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
package io.javaoperatorsdk.operator.baseapi.latestdistinct;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.latestdistinct.LatestDistinctTestReconciler.LABEL_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Latest Distinct with Multiple InformerEventSources",
    description =
        """
        Demonstrates using two separate InformerEventSource instances for ConfigMaps with \
        overlapping watches, combined with latestDistinctList() to deduplicate resources by \
        keeping the latest version. Also tests ReconcileUtils methods for patching resources \
        with proper cache updates.
        """)
class LatestDistinctIT {

  public static final String TEST_RESOURCE_NAME = "test-resource";
  public static final String CONFIG_MAP_1 = "config-map-1";
  public static final String DEFAULT_VALUE = "defaultValue";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(LatestDistinctTestReconciler.class)
          .build();

  @Test
  void testLatestDistinctListWithTwoInformerEventSources() {
    // Create the custom resource
    var resource = createTestCustomResource();
    resource = extension.create(resource);

    // Create ConfigMaps with type1 label (watched by first event source)
    var cm1 = createConfigMap(CONFIG_MAP_1, resource);
    extension.create(cm1);

    // Wait for reconciliation
    var reconciler = extension.getReconcilerOfType(LatestDistinctTestReconciler.class);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var updatedResource =
                  extension.get(LatestDistinctTestResource.class, TEST_RESOURCE_NAME);
              assertThat(updatedResource.getStatus()).isNotNull();
              // Should see 3 distinct ConfigMaps
              assertThat(updatedResource.getStatus().getConfigMapCount()).isEqualTo(1);
              assertThat(reconciler.isErrorOccurred()).isFalse();
              // note that since there are two event source, and we do the update through one event
              // source
              // the other will still propagate an event
              assertThat(reconciler.getNumberOfExecutions()).isEqualTo(2);
            });
  }

  private LatestDistinctTestResource createTestCustomResource() {
    var resource = new LatestDistinctTestResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(extension.getNamespace())
            .build());
    resource.setSpec(new LatestDistinctTestResourceSpec());
    return resource;
  }

  private ConfigMap createConfigMap(String name, LatestDistinctTestResource owner) {
    Map<String, String> labels = new HashMap<>();
    labels.put(LABEL_KEY, "val");

    Map<String, String> data = new HashMap<>();
    data.put("key", DEFAULT_VALUE);

    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(extension.getNamespace())
                .withLabels(labels)
                .build())
        .withData(data)
        .withNewMetadata()
        .withName(name)
        .withNamespace(extension.getNamespace())
        .withLabels(labels)
        .addNewOwnerReference()
        .withApiVersion(owner.getApiVersion())
        .withKind(owner.getKind())
        .withName(owner.getMetadata().getName())
        .withUid(owner.getMetadata().getUid())
        .endOwnerReference()
        .endMetadata()
        .build();
  }
}
