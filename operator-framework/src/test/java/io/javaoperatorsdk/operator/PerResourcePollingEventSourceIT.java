package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.perresourceeventsource.PerResourceEventSourceCustomResource;
import io.javaoperatorsdk.operator.sample.perresourceeventsource.PerResourcePollingEventSourceTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PerResourcePollingEventSourceIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PerResourcePollingEventSourceTestReconciler())
          .build();

  @Test
  void managedDependentsAreReconciledInOrder() {
    operator.create(PerResourceEventSourceCustomResource.class, resource());

    var reconciler =
        operator.getReconcilerOfType(PerResourcePollingEventSourceTestReconciler.class);
    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(2);
      assertThat(reconciler.getNumberOfFetchExecution()).isGreaterThan(2);
    });
  }

  private PerResourceEventSourceCustomResource resource() {
    var res = new PerResourceEventSourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    return res;
  }

}
