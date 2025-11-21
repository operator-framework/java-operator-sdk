package io.javaoperatorsdk.operator.dependent.dependentreinitialization;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Reusing Dependent Resource Instances Across Tests",
    description =
        """
        Demonstrates that dependent resource instances can be safely reused across multiple \
        operator start/stop cycles. This is particularly useful in CDI-managed environments \
        like Quarkus, where dependent resources are managed as beans and should be reusable \
        across test executions.
        """)
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

  private static void startEndStopOperator(
      KubernetesClient client, ConfigMapDependentResource dependent) {
    Operator o1 = new Operator(o -> o.withCloseClientOnStop(false).withKubernetesClient(client));
    o1.register(new DependentReInitializationReconciler(dependent));
    o1.start();
    o1.stop();
  }
}
