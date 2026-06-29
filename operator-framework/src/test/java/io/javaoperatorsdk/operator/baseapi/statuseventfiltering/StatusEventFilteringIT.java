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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.statuseventfiltering.StatusEventPrimaryReconciler.PRIMARY_NAME_LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Shared InformerEventSource status event filtering reproducer",
    description =
        """
        Reproduces an issue where a controller patches a secondary resource's status \
        through its own InformerEventSource. The EventFilterWindow suppresses the resulting \
        event, so the mapper (which guards on generation == observedGeneration) never fires \
        and the controller is never re-triggered by the secondary becoming ready.\
        """)
class StatusEventFilteringIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(StatusEventSecondaryReconciler.class)
          .withReconciler(StatusEventPrimaryReconciler.class)
          .build();

  @Test
  @Disabled("https://github.com/operator-framework/java-operator-sdk/issues/3445")
  void mapperShouldFireAfterStatusPatchThroughSharedEventSource() {
    var primaryReconciler = extension.getReconcilerOfType(StatusEventPrimaryReconciler.class);

    // Given: a secondary resource exists with generation=1 and no observedGeneration
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

    var primary = new StatusEventPrimaryResource();
    primary.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-primary")
            .withNamespace(extension.getNamespace())
            .build());

    // When: the primary resource is created, triggering reconciliation which patches
    //       the secondary's observedGeneration=1 through the shared InformerEventSource
    extension.create(primary);

    // Then: the mapper should see gen==obsGen and trigger a second reconciliation.
    //       With the bug, the EventFilterWindow suppresses the status-change event
    //       so the mapper never fires — the controller is stuck at 1 execution.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var sec = extension.get(StatusEventSecondaryResource.class, "test-secondary");
              assertThat(sec.getStatus().getObservedGeneration())
                  .as("reconciler should patch observedGeneration to match generation")
                  .isEqualTo(sec.getMetadata().getGeneration());
            });

    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThat(primaryReconciler.getNumberOfExecutions())
                    .as("mapper should fire for gen==obsGen and trigger re-reconciliation")
                    .isGreaterThanOrEqualTo(2));
  }
}
