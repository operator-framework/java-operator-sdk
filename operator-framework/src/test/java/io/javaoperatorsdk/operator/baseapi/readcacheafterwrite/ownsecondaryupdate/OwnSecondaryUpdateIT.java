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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownsecondaryupdate;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that when the controller updates a secondary resource through the read-cache-after-write
 * path (here: {@code context.resourceOperations().serverSideApply}), the resulting watch events on
 * the secondary are filtered and do NOT trigger additional reconciliations. Counterpart to {@code
 * ExternalSecondaryUpdateIT}, which asserts the opposite for third-party updates.
 */
class OwnSecondaryUpdateIT {

  static final String RESOURCE_NAME = "test-resource";

  OwnSecondaryUpdateReconciler reconciler = new OwnSecondaryUpdateReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void ownUpdateOnSecondaryDoesNotTriggerReconciliation() {
    operator.create(testResource());

    // Wait for the first reconciliation to have run all of its SSAs (the secondary CM exists
    // and carries the data of the last SSA iteration).
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var cm =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .withName(RESOURCE_NAME)
                      .get();
              assertThat(cm).isNotNull();
              assertThat(cm.getData())
                  .containsEntry("iteration", "" + OwnSecondaryUpdateReconciler.OWN_SSA_COUNT);
            });

    // Give any spurious own-write events time to reach the controller. The filter must absorb
    // them, so the reconciliation count must stay at 1 (the one triggered by the create).
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(reconciler.numberOfExecutions.get()).isEqualTo(1));
  }

  OwnSecondaryUpdateCustomResource testResource() {
    var r = new OwnSecondaryUpdateCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return r;
  }
}
