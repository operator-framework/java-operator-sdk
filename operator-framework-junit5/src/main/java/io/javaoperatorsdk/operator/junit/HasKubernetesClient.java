package io.javaoperatorsdk.operator.junit;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface HasKubernetesClient {
  /**
   * Returns the main Kubernetes client that is used to deploy the operator to the cluster.
   *
   * @return the main Kubernetes client
   */
  KubernetesClient getKubernetesClient();

  /**
   * Returns the Kubernetes client that is used to deploy infrastructure resources to the cluster
   * such as clusterroles, clusterrolebindings, etc. This client can be different from the main
   * client in case you need to test the operator with a different restrictions more closely
   * resembling the real restrictions it will have in production.
   *
   * @return the infrastructure Kubernetes client
   */
  KubernetesClient getInfrastructureKubernetesClient();
}
