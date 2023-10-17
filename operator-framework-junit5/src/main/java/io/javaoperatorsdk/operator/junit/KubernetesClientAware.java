package io.javaoperatorsdk.operator.junit;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * @deprecated It shouldn't be needed to pass a {@link KubernetesClient} instance to the reconciler
 *             anymore as the client should be accessed via {@link Context#getClient()} instead.
 */
@Deprecated(since = "4.5.0", forRemoval = true)
public interface KubernetesClientAware extends HasKubernetesClient {
  void setKubernetesClient(KubernetesClient kubernetesClient);
}
