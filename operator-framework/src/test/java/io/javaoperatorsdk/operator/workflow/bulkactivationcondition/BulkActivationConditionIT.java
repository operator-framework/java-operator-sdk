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
package io.javaoperatorsdk.operator.workflow.bulkactivationcondition;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.NoEventSourceForClassException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reproducer for the bug where NodeDeleteExecutor fires for a BulkDependentResource whose
 * activationCondition-gated event source has never been registered.
 *
 * <p>Workflow under test:
 *
 * <pre>
 * ConfigMapDependentResource  (reconcilePrecondition = AlwaysFailingPrecondition)
 *   └── SecretBulkDependentResource  (activationCondition = AlwaysTrueActivation)
 * </pre>
 *
 * <p>On first reconciliation with only a primary resource present: the ConfigMap precondition fails
 * → JOSDK calls markDependentsForDelete() → NodeDeleteExecutor fires for SecretBulkDependent →
 * SecretBulkDependent.getSecondaryResources() calls eventSourceRetriever.getEventSourceFor() → the
 * Secret event source was never registered (NodeReconcileExecutor never ran) →
 * NoEventSourceForClassException.
 *
 * <p>This test FAILS on unfixed JOSDK, demonstrating the bug.
 */
public class BulkActivationConditionIT {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new BulkActivationConditionReconciler())
          .build();

  @BeforeEach
  void resetError() {
    BulkActivationConditionReconciler.lastError.set(null);
  }

  @Test
  void nodeDeleteExecutorShouldNotThrowWhenEventSourceNotYetRegistered() {
    var primary = new BulkActivationConditionCustomResource();
    primary.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-primary")
            .withNamespace(extension.getNamespace())
            .build());
    extension.create(primary);

    // Wait for the error to arrive — the ConfigMap precondition always fails,
    // so JOSDK should attempt NodeDeleteExecutor for the Secret bulk dependent.
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> BulkActivationConditionReconciler.lastError.get() != null);

    // On unfixed JOSDK this fails: lastError is a NoEventSourceForClassException (or wraps one).
    // The assertion below demonstrates the bug by asserting the exception should NOT be present.
    Exception error = BulkActivationConditionReconciler.lastError.get();
    assertThat(error)
        .as(
            "NodeDeleteExecutor must not throw NoEventSourceForClassException when the"
                + " activationCondition-gated event source was never registered."
                + " Actual error: %s",
            error)
        .satisfies(
            e -> {
              Throwable t = e;
              while (t != null) {
                assertThat(t)
                    .as("Cause chain should not contain NoEventSourceForClassException")
                    .isNotInstanceOf(NoEventSourceForClassException.class);
                t = t.getCause();
              }
            });
  }
}
