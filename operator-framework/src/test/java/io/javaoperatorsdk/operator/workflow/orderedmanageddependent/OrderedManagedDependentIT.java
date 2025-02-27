package io.javaoperatorsdk.operator.workflow.orderedmanageddependent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
