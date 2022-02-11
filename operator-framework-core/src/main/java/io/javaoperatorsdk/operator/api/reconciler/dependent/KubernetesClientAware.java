package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface KubernetesClientAware {
  void setKubernetesClient(KubernetesClient kubernetesClient);
}
