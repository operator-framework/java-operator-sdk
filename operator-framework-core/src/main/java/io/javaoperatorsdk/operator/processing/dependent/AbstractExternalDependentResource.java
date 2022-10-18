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

  private final boolean isExplicitStateHandler = this instanceof ExplicitStateHandler;
  private final boolean isBulkDependentResource = this instanceof BulkDependentResource;
  @SuppressWarnings("rawtypes")
  private ExplicitStateHandler explicitStateHandler;
  private InformerEventSource<?, P> externalStateEventSource;
  private KubernetesClient kubernetesClient;

  @SuppressWarnings("unchecked")
  protected AbstractExternalDependentResource(Class<R> resourceType) {
    super(resourceType);
    if (isExplicitStateHandler) {
      explicitStateHandler = (ExplicitStateHandler<R, P, ?>) this;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void resolveEventSource(EventSourceRetriever<P> eventSourceRetriever) {
    super.resolveEventSource(eventSourceRetriever);
    if (isExplicitStateHandler) {
      externalStateEventSource = (InformerEventSource<?, P>) explicitStateHandler.eventSourceName()
          .map(n -> eventSourceRetriever
              .getResourceEventSourceFor(explicitStateHandler.stateResourceClass(), (String) n))
          .orElseGet(() -> eventSourceRetriever
              .getResourceEventSourceFor((Class<R>) explicitStateHandler.stateResourceClass()));
    }

  }

  @Override
  protected void onCreated(P primary, R created, Context<P> context) {
    super.onCreated(primary, created, context);
    if (this instanceof ExplicitStateHandler) {
      handleExplicitIDStoring(primary, created, context);
    }
  }

  @Override
  public void delete(P primary, Context<P> context) {
    if (isExplicitStateHandler && !isBulkDependentResource) {
      var secondary = getSecondaryResource(primary, context);
      super.delete(primary, context);
      // deletes the state after the resource is deleted
      handleExplicitIDDelete(primary, secondary.orElse(null), context);
    } else {
      super.delete(primary, context);
    }
  }

  @SuppressWarnings("unchecked")
  private void handleExplicitIDDelete(P primary, R secondary, Context<P> context) {
    var res = explicitStateHandler.stateResource(primary, secondary);
    explicitStateHandler.getKubernetesClient().resource(res).delete();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void handleExplicitIDStoring(P primary, R created, Context<P> context) {
    var resource = explicitStateHandler.stateResource(primary, created);
    var stateResource = explicitStateHandler.getKubernetesClient().resource(resource).create();
    if (externalStateEventSource != null) {
      ((RecentOperationCacheFiller) externalStateEventSource)
          .handleRecentResourceCreate(ResourceID.fromResource(primary), stateResource);
    }
  }


  @SuppressWarnings("unchecked")
  public void deleteBulkResource(P primary, R resource, String key,
      Context<P> context) {
    if (isExplicitStateHandler) {
      getKubernetesClient().resource(explicitStateHandler.stateResource(primary, resource))
          .delete();
    }
    handleDeleteBulkResource(primary, resource, key, context);
  }

  public void handleDeleteBulkResource(P primary, R resource, String key,
      Context<P> context) {
    throw new IllegalStateException("Override this method in case you manage an bulk resource");
  }


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
