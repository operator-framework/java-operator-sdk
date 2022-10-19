package io.javaoperatorsdk.operator.sample.clusterscopedresource;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class ClusterScopedCustomResourceReconciler
    implements Reconciler<ClusterScopedCustomResource>, Cleaner<ClusterScopedCustomResource>,
    TestExecutionInfoProvider,
    KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private KubernetesClient client;

  @Override
  public UpdateControl<ClusterScopedCustomResource> reconcile(
      ClusterScopedCustomResource resource, Context<ClusterScopedCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    client.configMaps().resource(desired(resource)).createOrReplace();

    resource.setStatus(new ClusterScopedCustomResourceStatus());
    resource.getStatus().setCreated(true);
    return UpdateControl.patchStatus(resource);
  }

  private ConfigMap desired(ClusterScopedCustomResource resource) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getSpec().getTargetNamespace())
            .build())
        .build();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
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
    client.configMaps().resource(desired(resource)).delete();
    return DeleteControl.defaultDelete();
  }
}
