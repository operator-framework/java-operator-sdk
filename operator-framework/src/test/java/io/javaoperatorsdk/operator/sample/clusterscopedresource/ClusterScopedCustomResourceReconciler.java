package io.javaoperatorsdk.operator.sample.clusterscopedresource;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;

@ControllerConfiguration
public class ClusterScopedCustomResourceReconciler
    implements Reconciler<ClusterScopedCustomResource>, Cleaner<ClusterScopedCustomResource>,
    KubernetesClientAware {

  public static final String DATA_KEY = "data-key";

  private KubernetesClient client;

  @Override
  public UpdateControl<ClusterScopedCustomResource> reconcile(
      ClusterScopedCustomResource resource, Context<ClusterScopedCustomResource> context) {

    final var desired = desired(resource);
    getConfigMapResource(desired).createOrReplace(desired);

    resource.setStatus(new ClusterScopedCustomResourceStatus());
    resource.getStatus().setCreated(true);
    return UpdateControl.patchStatus(resource);
  }

  private Resource<ConfigMap> getConfigMapResource(ConfigMap desired) {
    return client.configMaps().inNamespace(desired.getMetadata().getNamespace())
        .withName(desired.getMetadata().getName());
  }

  private ConfigMap desired(ClusterScopedCustomResource resource) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getSpec().getTargetNamespace())
            .build())
        .withData(Map.of(DATA_KEY, resource.getSpec().getData()))
        .build();
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  @Override
  public DeleteControl cleanup(ClusterScopedCustomResource resource,
      Context<ClusterScopedCustomResource> context) {
    final var desired = desired(resource);
    getConfigMapResource(desired).delete();
    return DeleteControl.defaultDelete();
  }
}
