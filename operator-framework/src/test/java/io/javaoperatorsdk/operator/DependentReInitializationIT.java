package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.dependentreinitialization.ConfigMapDependentResource;
import io.javaoperatorsdk.operator.sample.dependentreinitialization.DependentReInitializationCustomResource;
import io.javaoperatorsdk.operator.sample.dependentreinitialization.DependentReInitializationReconciler;

class DependentReInitializationIT {

  /**
   * In case dependent resource is managed by CDI (like in Quarkus) can be handy that the instance
   * is reused in tests.
   */
  @Test
  void dependentCanDeReInitialized() {
    var client = new KubernetesClientBuilder().build();
    LocallyRunOperatorExtension.applyCrd(DependentReInitializationCustomResource.class, client);

    var dependent = new ConfigMapDependentResource();

    startEndStopOperator(client, dependent);
    startEndStopOperator(client, dependent);
  }

  private static void startEndStopOperator(KubernetesClient client,
      ConfigMapDependentResource dependent) {
    Operator o1 = new Operator(o -> o
        .withCloseClientOnStop(false)
        .withKubernetesClient(client));
    o1.register(new DependentReInitializationReconciler(dependent, client));
    o1.start();
    o1.stop();
  }

}
