package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface KubernetesClientAware {
  void setKubernetesClient(KubernetesClient kubernetesClient);

  KubernetesClient getKubernetesClient();
}
