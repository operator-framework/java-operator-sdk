package io.javaoperatorsdk.operator.junit;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface KubernetesClientAware extends HasKubernetesClient {
  void setKubernetesClient(KubernetesClient kubernetesClient);
}
