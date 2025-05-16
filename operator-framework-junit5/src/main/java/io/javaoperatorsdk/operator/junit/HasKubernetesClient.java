package io.javaoperatorsdk.operator.junit;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface HasKubernetesClient {
  KubernetesClient getKubernetesClient();

  KubernetesClient getInfrastructureKubernetesClient();
}
