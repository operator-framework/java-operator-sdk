package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.orderedmanageddependent.ConfigMapDependentResource1;
import io.javaoperatorsdk.operator.sample.orderedmanageddependent.ConfigMapDependentResource2;
import io.javaoperatorsdk.operator.sample.orderedmanageddependent.OrderedManagedDependentCustomResource;
import io.javaoperatorsdk.operator.sample.orderedmanageddependent.OrderedManagedDependentTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderedManagedDependentIT {

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder().withReconciler(new OrderedManagedDependentTestReconciler())
          .build();

  @Test
  void managedDependentsAreReconciledInOrder() {
    operator.create(OrderedManagedDependentCustomResource.class, createTestResource());

    await().atMost(Duration.ofSeconds(5))
        .until(() -> ((OrderedManagedDependentTestReconciler) operator.getFirstReconciler())
            .getNumberOfExecutions() >= 1);
    // todo change to more precise values when event filtering is fixed
    // assertThat(OrderedManagedDependentTestReconciler.dependentExecution).hasSize(4);
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
