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
package io.javaoperatorsdk.operator.baseapi.statuseventfiltering;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.statuseventfiltering.StatusEventPrimaryReconciler.PRIMARY_NAME_LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Cross-controller status event filtering reproducer",
    description =
        """
        Reproduces an issue where two independent controllers run in the same operator. \
        A secondary controller reconciles its primary resource and patches its status \
        (observedGeneration = generation) while the primary controller patches its own \
        status. The primary controller watches secondaries via InformerEventSource. \
        The concurrent status patches may cause the secondary's status-change event to \
        be lost — leaving the primary controller unaware the secondary is ready.\
        """)
class StatusEventFilteringIT {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(StatusEventSecondaryReconciler.class)
          .withReconciler(StatusEventPrimaryReconciler.class)
          .build();

  @Test
  void mapperShouldFireAfterIndependentControllerPatchesStatus() {
    // Given: primary exists, secondary created and reaches steady state
    var primary = new StatusEventPrimaryResource();
    primary.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-primary")
            .withNamespace(extension.getNamespace())
            .build());
    extension.create(primary);

    var secondary = new StatusEventSecondaryResource();
    secondary.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-secondary")
            .withNamespace(extension.getNamespace())
            .withLabels(Map.of(PRIMARY_NAME_LABEL, "test-primary"))
            .build());
    secondary.setSpec(new StatusEventSecondaryResourceSpec());
    secondary.getSpec().setValue("initial");
    extension.create(secondary);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              var p = extension.get(StatusEventPrimaryResource.class, "test-primary");
              return Boolean.TRUE.equals(p.getStatus().getSecondaryReady());
            });

    // Arm the stall so the next primary reconciliation blocks before returning patchStatus
    var primaryReconciler = extension.getReconcilerOfType(StatusEventPrimaryReconciler.class);
    primaryReconciler.stallReconcile();

    // When: secondary spec changes, triggering the secondary controller to reconcile
    //       and patch its status via UpdateControl.patchStatus() (through
    // eventFilteringUpdateAndCacheResource)
    var current = extension.get(StatusEventSecondaryResource.class, "test-secondary");
    current.getSpec().setValue("updated");
    extension.replace(current);

    // Then: wait for secondary controller to patch its status (pure status-only WATCH event),
    //       then release the stalled primary.
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              var sec = extension.get(StatusEventSecondaryResource.class, "test-secondary");
              return Objects.equals(
                  sec.getStatus().getObservedGeneration(), sec.getMetadata().getGeneration());
            });
    primaryReconciler.releaseReconcile();

    // The primary was stalled during the spec-change event (secondaryReady=false).
    // After release it patches secondaryReady=false. The status-change event from
    // the secondary controller's patchStatus should trigger a re-reconciliation
    // where the primary sees secondaryReady=true.
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              var p = extension.get(StatusEventPrimaryResource.class, "test-primary");
              assertThat(p.getStatus().getSecondaryReady())
                  .as("primary should see secondary as ready after controller status patch")
                  .isTrue();
            });
  }
}
