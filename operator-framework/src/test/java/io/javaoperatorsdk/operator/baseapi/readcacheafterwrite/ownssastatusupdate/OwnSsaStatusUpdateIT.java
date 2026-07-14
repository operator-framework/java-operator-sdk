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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownssastatusupdate;

import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownssastatusupdate.OwnSsaStatusUpdateReconciler.EXTERNAL_LABEL_KEY;
import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownssastatusupdate.OwnSsaStatusUpdateReconciler.EXTERNAL_LABEL_VALUE;
import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownssastatusupdate.OwnSsaStatusUpdateReconciler.STATUS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that updating the status through {@code
 * resourceOperations().serverSideApplyPrimaryStatus(...)} (instead of returning an {@code
 * UpdateControl}) is read-cache-after-write consistent and does not loop: the own SSA status write
 * is filtered as an own event, so the controller converges. A subsequent external label change must
 * still be picked up by a fresh reconciliation.
 */
class OwnSsaStatusUpdateIT {

  static final String RESOURCE_NAME = "test-resource";

  OwnSsaStatusUpdateReconciler reconciler = new OwnSsaStatusUpdateReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void ssaStatusUpdateIsConsistentAndDoesNotLoop() {
    extension.create(testResource());

    // the status is persisted via the own SSA status update
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var actual = extension.get(OwnSsaStatusUpdateCustomResource.class, RESOURCE_NAME);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getValue()).isEqualTo(STATUS_VALUE);
            });

    // the own status write must be filtered: no reconciliation loop
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(reconciler.numberOfExecutions.get())
                    .as("own SSA status update must not trigger a reconciliation loop")
                    .isLessThanOrEqualTo(2));

    var executionsBeforeExternalUpdate = reconciler.numberOfExecutions.get();

    // an external party changes a label; this must still trigger a fresh reconciliation
    var current = extension.get(OwnSsaStatusUpdateCustomResource.class, RESOURCE_NAME);
    var labels = new HashMap<String, String>();
    if (current.getMetadata().getLabels() != null) {
      labels.putAll(current.getMetadata().getLabels());
    }
    labels.put(EXTERNAL_LABEL_KEY, EXTERNAL_LABEL_VALUE);
    current.getMetadata().setLabels(labels);
    extension.replace(current);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.numberOfExecutions.get())
                  .isGreaterThan(executionsBeforeExternalUpdate);
              assertThat(reconciler.externalLabelSeenInLaterReconciliation.get())
                  .as("a later reconciliation must observe the externally-applied label")
                  .isTrue();
            });
  }

  OwnSsaStatusUpdateCustomResource testResource() {
    var r = new OwnSsaStatusUpdateCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return r;
  }
}
