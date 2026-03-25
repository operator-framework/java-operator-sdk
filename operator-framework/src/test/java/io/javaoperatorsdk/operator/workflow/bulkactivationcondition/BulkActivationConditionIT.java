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
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

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
 * <p>On first reconciliation the ConfigMap precondition fails → JOSDK calls
 * markDependentsForDelete() → NodeDeleteExecutor fires for SecretBulkDependent →
 * SecretBulkDependent.getSecondaryResources() calls eventSourceRetriever.getEventSourceFor() → the
 * Secret event source was never registered (NodeReconcileExecutor never ran) →
 * NoEventSourceForClassException.
 *
 * <p>This test FAILS on unfixed JOSDK, demonstrating the bug.
 */
@Sample(
    tldr = "Bulk Dependent Resource with Activation Condition Bug Reproducer",
    description =
        """
        Reproducer for a bug where NodeDeleteExecutor fires for a BulkDependentResource \
        with an activationCondition before its event source has been registered, \
        causing NoEventSourceForClassException. Triggered when a parent dependent \
        has a failing reconcilePrecondition on first reconciliation.
        """)
public class BulkActivationConditionIT {

  static final BulkActivationConditionReconciler reconciler =
      new BulkActivationConditionReconciler();

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @BeforeEach
  void reset() {
    reconciler.lastError.set(null);
    reconciler.callCount.set(0);
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

    // Wait for reconcile() to be called.
    // If the bug is present, SecretBulkDependentResource will be in error and lastError will be set
    await().atMost(Duration.ofSeconds(10)).until(() -> reconciler.callCount.get() == 1);

    // On unfixed JOSDK this fails: lastError contains NoEventSourceForClassException.
    assertThat(reconciler.lastError.get()).isNull();
  }
}
