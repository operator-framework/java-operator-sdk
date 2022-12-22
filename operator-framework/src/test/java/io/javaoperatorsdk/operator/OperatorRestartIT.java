package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.restart.RestartTestCustomResource;
import io.javaoperatorsdk.operator.sample.restart.RestartTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OperatorRestartIT {

  @Test
  void operatorCanBeRestarted() {
    try (var client = new KubernetesClientBuilder().build()) {
      LocallyRunOperatorExtension.applyCrd(RestartTestCustomResource.class,
          client);
      // TODO check if this is good enough for Quarkus dev mode
      Operator operator = new Operator(o -> o.withCloseClientOnStop(false));
      var reconciler = new RestartTestReconciler();
      operator.register(reconciler);
      operator.start();

      client.resource(testCustomResource()).createOrReplace();
      await().untilAsserted(() -> {
        assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0);
      });
      var reconcileNumberBeforeStop = reconciler.getNumberOfExecutions();
      operator.stop();
      operator.start();

      await().untilAsserted(() -> {
        assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(reconcileNumberBeforeStop);
      });
    }
  }

  RestartTestCustomResource testCustomResource() {
    RestartTestCustomResource cr = new RestartTestCustomResource();
    cr.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    return cr;
  }
}
