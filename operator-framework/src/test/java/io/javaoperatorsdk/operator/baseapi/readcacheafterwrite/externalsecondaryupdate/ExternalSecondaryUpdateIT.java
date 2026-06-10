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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalsecondaryupdate;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalsecondaryupdate.ExternalSecondaryUpdateReconciler.EXTERNAL_LABEL_KEY;
import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalsecondaryupdate.ExternalSecondaryUpdateReconciler.EXTERNAL_LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that when a secondary resource (a ConfigMap owned by the primary) is modified externally
 * between two caching+filtering updates from the controller, the external change is NOT silently
 * absorbed: a later reconciliation must observe it through the merged temp/informer cache.
 */
class ExternalSecondaryUpdateIT {

  static final String RESOURCE_NAME = "test-resource";

  ExternalSecondaryUpdateReconciler reconciler = new ExternalSecondaryUpdateReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void externalUpdateOnSecondaryDuringFilteringUpdatePropagates() throws InterruptedException {
    operator.create(testResource());

    // wait for the reconciler to enter the first reconciliation and create the secondary CM
    assertThat(reconciler.firstReconcileEntered.await(30, TimeUnit.SECONDS))
        .as("reconciler must enter first reconciliation")
        .isTrue();

    // a third party adds a label to the secondary CM while we are mid-reconcile
    var cm =
        operator
            .getKubernetesClient()
            .configMaps()
            .inNamespace(operator.getNamespace())
            .withName(RESOURCE_NAME)
            .get();
    assertThat(cm).as("secondary CM created by reconciler").isNotNull();
    var labels = new HashMap<String, String>();
    if (cm.getMetadata().getLabels() != null) {
      labels.putAll(cm.getMetadata().getLabels());
    }
    labels.put(EXTERNAL_LABEL_KEY, EXTERNAL_LABEL_VALUE);
    cm.getMetadata().setLabels(labels);
    operator.getKubernetesClient().resource(cm).inNamespace(operator.getNamespace()).replace();

    // signal the reconciler to issue the second caching+filtering SSA
    reconciler.externalUpdateApplied.countDown();

    // a later reconciliation, triggered by the external label event, must see the label
    // through the cache (informer + temp cache merge).
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.numberOfExecutions.get())
                  .as("external CM update must trigger a fresh reconciliation")
                  .isGreaterThanOrEqualTo(2);
              assertThat(reconciler.externalLabelSeenInLaterReconciliation.get())
                  .as("a later reconciliation must observe the externally-applied label")
                  .isTrue();
            });

    // the second SSA from the reconciler did go through and was captured
    assertThat(reconciler.rvAfterCachingFilteringUpdate.get()).isNotNull();
    var finalCm =
        operator
            .getKubernetesClient()
            .configMaps()
            .inNamespace(operator.getNamespace())
            .withName(RESOURCE_NAME)
            .get();
    assertThat(finalCm.getMetadata().getLabels())
        .as("external label preserved on the secondary after the SSA")
        .containsEntry(EXTERNAL_LABEL_KEY, EXTERNAL_LABEL_VALUE);
  }

  ExternalSecondaryUpdateCustomResource testResource() {
    var r = new ExternalSecondaryUpdateCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return r;
  }
}
