package io.javaoperatorsdk.operator.dependent.standalonedependent;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class StandaloneDependentTestReconciler
    implements Reconciler<StandaloneDependentTestCustomResource> {
  private volatile boolean errorOccurred = false;

  DeploymentDependentResource deploymentDependent;

  public StandaloneDependentTestReconciler() {
    deploymentDependent = new DeploymentDependentResource();
  }

  @Override
  public List<EventSource<?, StandaloneDependentTestCustomResource>> prepareEventSources(
      EventSourceContext<StandaloneDependentTestCustomResource> context) {
    return EventSourceUtils.dependentEventSources(context, deploymentDependent);
  }

  @Override
  public UpdateControl<StandaloneDependentTestCustomResource> reconcile(
      StandaloneDependentTestCustomResource primary,
      Context<StandaloneDependentTestCustomResource> context) {
    deploymentDependent.reconcile(primary, context);
    Optional<Deployment> deployment = context.getSecondaryResource(Deployment.class);
    if (deployment.isEmpty()) {
      throw new IllegalStateException("Resource should not be empty after reconcile.");
    }

    if (deployment.get().getSpec().getReplicas() != primary.getSpec().getReplicaCount()) {
      // see https://github.com/operator-framework/java-operator-sdk/issues/924
      throw new IllegalStateException("Something went wrong with the cache mechanism.");
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public ErrorStatusUpdateControl<StandaloneDependentTestCustomResource> updateErrorStatus(
      StandaloneDependentTestCustomResource resource,
      Context<StandaloneDependentTestCustomResource> context,
      Exception e) {
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

  private static class DeploymentDependentResource
      extends CRUDKubernetesDependentResource<Deployment, StandaloneDependentTestCustomResource> {

    @Override
    protected Deployment desired(
        StandaloneDependentTestCustomResource primary,
        Context<StandaloneDependentTestCustomResource> context) {
      Deployment deployment =
          ReconcilerUtils.loadYaml(
              Deployment.class,
              StandaloneDependentResourceIT.class,
              "/io/javaoperatorsdk/operator/nginx-deployment.yaml");
      deployment.getMetadata().setName(primary.getMetadata().getName());
      deployment.getSpec().setReplicas(primary.getSpec().getReplicaCount());
      deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      return deployment;
    }
  }
}
