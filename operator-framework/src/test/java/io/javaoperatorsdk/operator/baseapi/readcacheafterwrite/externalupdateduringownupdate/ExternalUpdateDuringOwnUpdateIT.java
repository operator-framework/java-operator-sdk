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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalupdateduringownupdate;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalupdateduringownupdate.ExternalUpdateDuringOwnUpdateReconciler.EXTERNAL_LABEL_KEY;
import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalupdateduringownupdate.ExternalUpdateDuringOwnUpdateReconciler.EXTERNAL_LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that an external update arriving while the controller's own filter window is open is NOT
 * mistakenly filtered. The third-party event must propagate as a fresh reconciliation in which the
 * reconciler observes the externally-applied change.
 */
class ExternalUpdateDuringOwnUpdateIT {

  static final String RESOURCE_NAME = "test-resource";

  ExternalUpdateDuringOwnUpdateReconciler reconciler =
      new ExternalUpdateDuringOwnUpdateReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void externalUpdateDuringOwnUpdateTriggersFreshReconciliation() throws InterruptedException {
    extension.create(testResource());

    assertThat(reconciler.updateStartedLatch.await(30, TimeUnit.SECONDS))
        .as("reconciler should enter the patch update operation")
        .isTrue();

    // external party modifies a label while our filter window is still open
    var current = extension.get(ExternalUpdateDuringOwnUpdateCustomResource.class, RESOURCE_NAME);
    var labels = new HashMap<String, String>();
    if (current.getMetadata().getLabels() != null) {
      labels.putAll(current.getMetadata().getLabels());
    }
    labels.put(EXTERNAL_LABEL_KEY, EXTERNAL_LABEL_VALUE);
    current.getMetadata().setLabels(labels);
    extension.replace(current);

    // signal reconciler to complete its own status update
    reconciler.externalUpdateDoneLatch.countDown();

    // the external update event must NOT be silently absorbed by the filter window;
    // a fresh reconciliation must observe the external label.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.numberOfExecutions.get()).isGreaterThanOrEqualTo(2);
              assertThat(reconciler.externalLabelSeenInLaterReconciliation.get())
                  .as("a later reconciliation must observe the externally-applied label")
                  .isTrue();
            });
  }

  ExternalUpdateDuringOwnUpdateCustomResource testResource() {
    var r = new ExternalUpdateDuringOwnUpdateCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return r;
  }
}
