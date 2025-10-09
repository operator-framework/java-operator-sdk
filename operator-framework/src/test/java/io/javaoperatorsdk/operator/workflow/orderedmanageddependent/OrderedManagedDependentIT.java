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
package io.javaoperatorsdk.operator.workflow.orderedmanageddependent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Ordered Managed Dependent Resources",
    description =
        """
        Demonstrates how to control the order of reconciliation for managed dependent resources. \
        This test verifies that dependent resources are reconciled in a specific sequence, \
        ensuring proper orchestration when dependencies have ordering requirements.
        """)
class OrderedManagedDependentIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new OrderedManagedDependentTestReconciler())
          .build();

  @Test
  void managedDependentsAreReconciledInOrder() {
    operator.create(createTestResource());

    await()
        .pollDelay(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                ((OrderedManagedDependentTestReconciler) operator.getFirstReconciler())
                        .getNumberOfExecutions()
                    == 1);

    assertThat(OrderedManagedDependentTestReconciler.dependentExecution.get(0))
        .isEqualTo(ConfigMapDependentResource1.class);
    assertThat(OrderedManagedDependentTestReconciler.dependentExecution.get(1))
        .isEqualTo(ConfigMapDependentResource2.class);
  }

  private OrderedManagedDependentCustomResource createTestResource() {
    OrderedManagedDependentCustomResource cr = new OrderedManagedDependentCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName("test");
    return cr;
  }
}
