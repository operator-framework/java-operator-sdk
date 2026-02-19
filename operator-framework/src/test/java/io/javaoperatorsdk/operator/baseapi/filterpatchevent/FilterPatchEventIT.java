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
package io.javaoperatorsdk.operator.baseapi.filterpatchevent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Controlling patch event filtering in UpdateControl",
    description =
        """
        Demonstrates how to use the filterPatchEvent parameter in UpdateControl to control \
        whether patch operations trigger subsequent reconciliation events. When filterPatchEvent \
        is true (default), patch events are filtered out to prevent reconciliation loops. When \
        false, patch events trigger reconciliation, allowing for controlled event propagation.
        """)
class FilterPatchEventIT {

  public static final int POLL_DELAY = 150;
  public static final String NAME = "test1";
  public static final String UPDATED = "updated";

  FilterPatchEventTestReconciler reconciler = new FilterPatchEventTestReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void patchEventFilteredWhenFlagIsTrue() {
    reconciler.setFilterPatchEvent(true);
    var resource = createTestResource();
    extension.create(resource);

    // Wait for the reconciliation to complete and the resource to be updated
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () -> {
              var updated = extension.get(FilterPatchEventTestCustomResource.class, NAME);
              assertThat(updated.getStatus()).isNotNull();
              assertThat(updated.getStatus().getValue()).isEqualTo(UPDATED);
            });

    // With filterPatchEvent=true, reconciliation should only run once
    // (triggered by the initial create, but not by the patch operation)
    int executions = reconciler.getNumberOfExecutions();
    assertThat(executions).isEqualTo(1);
  }

  @Test
  void patchEventNotFilteredWhenFlagIsFalse() {
    reconciler.setFilterPatchEvent(false);
    var resource = createTestResource();
    extension.create(resource);

    // Wait for the reconciliation to complete and the resource to be updated
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var updated = extension.get(FilterPatchEventTestCustomResource.class, NAME);
              assertThat(updated.getStatus()).isNotNull();
              assertThat(updated.getStatus().getValue()).isEqualTo(UPDATED);
            });

    // Wait for potential additional reconciliations
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              int executions = reconciler.getNumberOfExecutions();
              // With filterPatchEvent=false, reconciliation should run at least twice
              // (once for create and at least once for the patch event)
              assertThat(executions).isGreaterThanOrEqualTo(2);
            });
  }

  private FilterPatchEventTestCustomResource createTestResource() {
    FilterPatchEventTestCustomResource resource = new FilterPatchEventTestCustomResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName(NAME);
    return resource;
  }
}
