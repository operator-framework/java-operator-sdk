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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.onrelistfilter;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the re-list aware filtering of own writes on a secondary resource:
 *
 * <ul>
 *   <li>no re-list — own write is filtered, no extra reconciliation
 *   <li>re-list around the whole update window — own write is propagated
 *   <li>re-list completes BEFORE the update — own write is filtered
 *   <li>re-list starts WHILE the update window is open — own write is propagated
 * </ul>
 */
class OnRelistFilterIT {

  static final String RESOURCE_NAME = "test-resource";

  OnRelistFilterReconciler reconciler = new OnRelistFilterReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void ownSecondaryWriteIsFilteredWithoutRelist() {
    reconciler.setMode(OnRelistFilterReconciler.Mode.NO_RELIST);
    operator.create(testResource());

    assertOnlyInitialReconciliation();
  }

  @Test
  void ownSecondaryWriteIsPropagatedWhenRelistWrapsTheUpdate() {
    reconciler.setMode(OnRelistFilterReconciler.Mode.RELIST_AROUND_UPDATE);
    operator.create(testResource());

    assertExtraReconciliationTriggered();
  }

  @Test
  void ownSecondaryWriteIsFilteredWhenRelistCompletesBeforeTheUpdate() {
    reconciler.setMode(OnRelistFilterReconciler.Mode.RELIST_COMPLETES_BEFORE_UPDATE);
    operator.create(testResource());

    assertOnlyInitialReconciliation();
  }

  @Test
  void ownSecondaryWriteIsPropagatedWhenRelistStartsDuringTheUpdate() {
    reconciler.setMode(OnRelistFilterReconciler.Mode.RELIST_STARTS_DURING_UPDATE);
    operator.create(testResource());

    assertExtraReconciliationTriggered();
  }

  private void assertExtraReconciliationTriggered() {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(reconciler.getNumberOfExecutions())
                    .as("watch event during re-list must trigger a fresh reconciliation")
                    .isGreaterThanOrEqualTo(2));
  }

  private void assertOnlyInitialReconciliation() {
    await()
        .pollDelay(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(reconciler.getNumberOfExecutions())
                    .as("own write must be filtered, no extra reconciliation expected")
                    .isEqualTo(1));
  }

  private OnRelistFilterCustomResource testResource() {
    var r = new OnRelistFilterCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return r;
  }
}
