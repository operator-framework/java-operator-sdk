package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public abstract class AbstractExternalDependentResource<R, P extends HasMetadata, T extends ResourceEventSource<R, P>>
    extends AbstractEventSourceHolderDependentResource<R, P, T> {

  private final boolean isDependentResourceWithExplicitState =
      this instanceof DependentResourceWithExplicitState;
  private final boolean isBulkDependentResource = this instanceof BulkDependentResource;
  @SuppressWarnings("rawtypes")
  private DependentResourceWithExplicitState dependentResourceWithExplicitState;
  private InformerEventSource<?, P> externalStateEventSource;
  private KubernetesClient kubernetesClient;

  @SuppressWarnings("unchecked")
  protected AbstractExternalDependentResource(Class<R> resourceType) {
    super(resourceType);
    if (isDependentResourceWithExplicitState) {
      dependentResourceWithExplicitState = (DependentResourceWithExplicitState<R, P, ?>) this;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void resolveEventSource(EventSourceRetriever<P> eventSourceRetriever) {
    super.resolveEventSource(eventSourceRetriever);
    if (isDependentResourceWithExplicitState) {
      final var eventSourceName = (String) dependentResourceWithExplicitState
          .eventSourceName().orElse(null);
      externalStateEventSource = (InformerEventSource<?, P>) eventSourceRetriever
          .getResourceEventSourceFor(dependentResourceWithExplicitState.stateResourceClass(),
              eventSourceName);
    }

  }

  @Override
  protected void onCreated(P primary, R created, Context<P> context) {
    super.onCreated(primary, created, context);
    if (this instanceof DependentResourceWithExplicitState) {
      handleExplicitStateCreation(primary, created, context);
    }
  }

  @Override
  public void delete(P primary, Context<P> context) {
    if (isDependentResourceWithExplicitState && !isBulkDependentResource) {
      var secondary = getSecondaryResource(primary, context);
      super.delete(primary, context);
      // deletes the state after the resource is deleted
      handleExplicitStateDelete(primary, secondary.orElse(null), context);
    } else {
      super.delete(primary, context);
    }
  }

  @SuppressWarnings({"unchecked", "unused"})
  private void handleExplicitStateDelete(P primary, R secondary, Context<P> context) {
    var res = dependentResourceWithExplicitState.stateResource(primary, secondary);
    dependentResourceWithExplicitState.getKubernetesClient().resource(res).delete();
  }

  @SuppressWarnings({"rawtypes", "unchecked", "unused"})
  protected void handleExplicitStateCreation(P primary, R created, Context<P> context) {
    var resource = dependentResourceWithExplicitState.stateResource(primary, created);
    var stateResource =
        dependentResourceWithExplicitState.getKubernetesClient().resource(resource).create();
    if (externalStateEventSource != null) {
      ((RecentOperationCacheFiller) externalStateEventSource)
          .handleRecentResourceCreate(ResourceID.fromResource(primary), stateResource);
    }
  }


  @SuppressWarnings("unchecked")
  public void deleteTargetResource(P primary, R resource, String key,
      Context<P> context) {
    if (isDependentResourceWithExplicitState) {
      getKubernetesClient()
          .resource(dependentResourceWithExplicitState.stateResource(primary, resource))
          .delete();
    }
    handleDeleteTargetResource(primary, resource, key, context);
  }

  public void handleDeleteTargetResource(P primary, R resource, String key,
      Context<P> context) {
    throw new IllegalStateException("Override this method in case you manage an bulk resource");
  }

  @SuppressWarnings("rawtypes")
  protected InformerEventSource getExternalStateEventSource() {
    return externalStateEventSource;
  }

  /**
   * It's here just to manage the explicit state resource in case the dependent resource implements
   * {@link RecentOperationCacheFiller}.
   *
   * @return kubernetes client.
   */
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }
}
