package io.javaoperatorsdk.operator.sample.standalonedependent;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Creator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Updater;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
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
  public List<EventSource> prepareEventSources(
      EventSourceContext<StandaloneDependentTestCustomResource> context) {
    return List.of(deploymentDependent.eventSource(context));
  }

  @Override
  public UpdateControl<StandaloneDependentTestCustomResource> reconcile(
      StandaloneDependentTestCustomResource primary, Context context) {
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
  public Optional<StandaloneDependentTestCustomResource> updateErrorStatus(
      StandaloneDependentTestCustomResource resource, RetryInfo retryInfo, RuntimeException e) {
    errorOccurred = true;
    return Optional.empty();
  }

  public boolean isErrorOccurred() {
    return errorOccurred;
  }

  private static class DeploymentDependentResource extends
      KubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource>
      implements Creator<Deployment, StandaloneDependentTestCustomResource>,
      Updater<Deployment, StandaloneDependentTestCustomResource> {

    @Override
    protected Deployment desired(StandaloneDependentTestCustomResource primary, Context context) {
      Deployment deployment =
          ReconcilerUtils.loadYaml(Deployment.class, getClass(), "nginx-deployment.yaml");
      deployment.getMetadata().setName(primary.getMetadata().getName());
      deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      return deployment;
    }
  }
}
