package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * @deprecated It shouldn't be needed to pass a {@link KubernetesClient} instance anymore as the
 *             client should be accessed via {@link Context#getClient()} instead.
 */
@Deprecated(since = "4.5.0", forRemoval = true)
public interface KubernetesClientAware {
  void setKubernetesClient(KubernetesClient kubernetesClient);

  KubernetesClient getKubernetesClient();
}
