package io.javaoperatorsdk.operator.sample.standalonedependent;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class StandaloneDependentTestReconciler
    implements Reconciler<StandaloneDependentTestCustomResource>,
    EventSourceInitializer<StandaloneDependentTestCustomResource>,
    KubernetesClientAware, ErrorStatusHandler<StandaloneDependentTestCustomResource> {

  private KubernetesClient kubernetesClient;
  private boolean errorOccurred = false;

  DeploymentDependentResource deploymentDependent;

  public StandaloneDependentTestReconciler() {
    deploymentDependent = new DeploymentDependentResource();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<StandaloneDependentTestCustomResource> context) {
    return Map.of("deployment", deploymentDependent.initEventSource(context));
  }

  @Override
  public UpdateControl<StandaloneDependentTestCustomResource> reconcile(
      StandaloneDependentTestCustomResource primary,
      Context<StandaloneDependentTestCustomResource> context) {
    deploymentDependent.reconcile(primary, context);
    Optional<Deployment> deployment = deploymentDependent.getResource(primary);
    if (deployment.isEmpty()) {
      throw new IllegalStateException("Resource should not be empty after reconcile.");
    }

    if (deployment.get().getSpec().getReplicas() != primary.getSpec().getReplicaCount()) {
      // see https://github.com/java-operator-sdk/java-operator-sdk/issues/924
      throw new IllegalStateException("Something went wrong withe the cache mechanism.");
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    deploymentDependent.setKubernetesClient(kubernetesClient);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return this.kubernetesClient;
  }

  @Override
  public ErrorStatusUpdateControl<StandaloneDependentTestCustomResource> updateErrorStatus(
      StandaloneDependentTestCustomResource resource,
      Context<StandaloneDependentTestCustomResource> context, Exception e) {
    // this can happen when a namespace is terminated in test
    if (e instanceof KubernetesClientException) {
      return ErrorStatusUpdateControl.noStatusUpdate();
    }
    errorOccurred = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorOccurred() {
    return errorOccurred;
  }

  private static class DeploymentDependentResource extends
      KubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource>
      implements Creator<Deployment, StandaloneDependentTestCustomResource>,
      Updater<Deployment, StandaloneDependentTestCustomResource> {

    public DeploymentDependentResource() {
      super(Deployment.class);
    }

    @Override
    protected Deployment desired(StandaloneDependentTestCustomResource primary,
        Context<StandaloneDependentTestCustomResource> context) {
      Deployment deployment =
          ReconcilerUtils.loadYaml(Deployment.class, getClass(), "nginx-deployment.yaml");
      deployment.getMetadata().setName(primary.getMetadata().getName());
      deployment.getSpec().setReplicas(primary.getSpec().getReplicaCount());
      deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      return deployment;
    }
  }
}
