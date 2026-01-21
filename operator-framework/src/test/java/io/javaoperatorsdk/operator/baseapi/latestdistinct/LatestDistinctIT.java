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
import static io.javaoperatorsdk.operator.baseapi.latestdistinct.LatestDistinctTestReconciler.LABEL_TYPE_1;
import static io.javaoperatorsdk.operator.baseapi.latestdistinct.LatestDistinctTestReconciler.LABEL_TYPE_2;
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
  public static final String CONFIG_MAP_2 = "config-map-2";
  public static final String CONFIG_MAP_3 = "config-map-3";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(LatestDistinctTestReconciler.class)
          .build();

  @Test
  void testLatestDistinctListWithTwoInformerEventSources() {
    // Create the custom resource
    var resource = createTestCustomResource();
    operator.create(resource);

    // Create ConfigMaps with type1 label (watched by first event source)
    var cm1 = createConfigMap(CONFIG_MAP_1, LABEL_TYPE_1, resource, "value1");
    operator.create(cm1);

    var cm2 = createConfigMap(CONFIG_MAP_2, LABEL_TYPE_1, resource, "value2");
    operator.create(cm2);

    // Create ConfigMap with type2 label (watched by second event source)
    var cm3 = createConfigMap(CONFIG_MAP_3, LABEL_TYPE_2, resource, "value3");
    operator.create(cm3);

    // Wait for reconciliation
    var reconciler = operator.getReconcilerOfType(LatestDistinctTestReconciler.class);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(1);
              var updatedResource =
                  operator.get(LatestDistinctTestResource.class, TEST_RESOURCE_NAME);
              assertThat(updatedResource.getStatus()).isNotNull();
              // Should see 3 distinct ConfigMaps
              assertThat(updatedResource.getStatus().getConfigMapCount()).isEqualTo(3);
              assertThat(updatedResource.getStatus().getDataFromConfigMaps())
                  .isEqualTo("value1,value2,value3");
              // Verify ReconcileUtils was used
              assertThat(updatedResource.getStatus().isReconcileUtilsCalled()).isTrue();
            });

    // Verify distinct ConfigMap names
    assertThat(reconciler.getDistinctConfigMapNames())
        .containsExactlyInAnyOrder(CONFIG_MAP_1, CONFIG_MAP_2, CONFIG_MAP_3);
  }

  @Test
  void testLatestDistinctDeduplication() {
    // Create the custom resource
    var resource = createTestCustomResource();
    operator.create(resource);

    // Create a ConfigMap with type1 label
    var cm1 = createConfigMap(CONFIG_MAP_1, LABEL_TYPE_1, resource, "initialValue");
    operator.create(cm1);

    // Wait for initial reconciliation
    var reconciler = operator.getReconcilerOfType(LatestDistinctTestReconciler.class);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var updatedResource =
                  operator.get(LatestDistinctTestResource.class, TEST_RESOURCE_NAME);
              assertThat(updatedResource.getStatus()).isNotNull();
              assertThat(updatedResource.getStatus().getConfigMapCount()).isEqualTo(1);
              assertThat(updatedResource.getStatus().getDataFromConfigMaps())
                  .isEqualTo("initialValue");
            });

    int executionsBeforeUpdate = reconciler.getNumberOfExecutions();

    // Update the ConfigMap
    cm1 = operator.get(ConfigMap.class, CONFIG_MAP_1);
    cm1.getData().put("key", "updatedValue");
    operator.replace(cm1);

    // Wait for reconciliation after update
    await()
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(executionsBeforeUpdate);
              var updatedResource =
                  operator.get(LatestDistinctTestResource.class, TEST_RESOURCE_NAME);
              assertThat(updatedResource.getStatus()).isNotNull();
              // Still should see only 1 distinct ConfigMap (same name, updated version)
              assertThat(updatedResource.getStatus().getConfigMapCount()).isEqualTo(1);
              assertThat(updatedResource.getStatus().getDataFromConfigMaps())
                  .isEqualTo("updatedValue");
            });
  }

  @Test
  void testReconcileUtilsServerSideApply() {
    // Create the custom resource with initial spec value
    var resource = createTestCustomResource();
    resource.getSpec().setValue("initialSpecValue");
    operator.create(resource);

    // Create a ConfigMap
    var cm1 = createConfigMap(CONFIG_MAP_1, LABEL_TYPE_1, resource, "value1");
    operator.create(cm1);

    // Wait for reconciliation
    var reconciler = operator.getReconcilerOfType(LatestDistinctTestReconciler.class);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var updatedResource =
                  operator.get(LatestDistinctTestResource.class, TEST_RESOURCE_NAME);
              assertThat(updatedResource.getStatus()).isNotNull();
              assertThat(updatedResource.getStatus().isReconcileUtilsCalled()).isTrue();
              // Verify that the status was updated using ReconcileUtils.serverSideApplyStatus
              assertThat(updatedResource.getStatus().getConfigMapCount()).isEqualTo(1);
            });

    // Verify no errors occurred
    assertThat(reconciler.isErrorOccurred()).isFalse();
  }

  private LatestDistinctTestResource createTestCustomResource() {
    var resource = new LatestDistinctTestResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(operator.getNamespace())
            .build());
    resource.setSpec(new LatestDistinctTestResourceSpec());
    return resource;
  }

  private ConfigMap createConfigMap(
      String name, String labelValue, LatestDistinctTestResource owner, String dataValue) {
    Map<String, String> labels = new HashMap<>();
    labels.put(LABEL_KEY, labelValue);

    Map<String, String> data = new HashMap<>();
    data.put("key", dataValue);

    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(operator.getNamespace())
                .withLabels(labels)
                .build())
        .withData(data)
        .withNewMetadata()
        .withName(name)
        .withNamespace(operator.getNamespace())
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
