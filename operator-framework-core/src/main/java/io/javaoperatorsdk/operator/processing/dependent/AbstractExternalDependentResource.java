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


  private final boolean isExplicitIDHandler = this instanceof ExplicitIDHandler;
  private final boolean isBulkDependentResource = this instanceof BulkDependentResource;
  private ExplicitIDHandler<R, P, ?> explicitIDHandler;
  private InformerEventSource<?, P> externalStateEventSource;
  private KubernetesClient kubernetesClient;

  @SuppressWarnings("unchecked")
  protected AbstractExternalDependentResource(Class<R> resourceType) {
    super(resourceType);
    if (isExplicitIDHandler) {
      explicitIDHandler = (ExplicitIDHandler<R, P, ?>) this;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void resolveEventSource(EventSourceRetriever<P> eventSourceRetriever) {
    super.resolveEventSource(eventSourceRetriever);
    if (isExplicitIDHandler) {
      externalStateEventSource = (InformerEventSource<?, P>) explicitIDHandler.eventSourceName()
          .map(n -> eventSourceRetriever
              .getResourceEventSourceFor((Class<R>) explicitIDHandler.stateResourceClass(), n))
          .orElseGet(() -> eventSourceRetriever
              .getResourceEventSourceFor((Class<R>) explicitIDHandler.stateResourceClass()));
    }

  }

  @Override
  protected void onCreated(P primary, R created, Context<P> context) {
    super.onCreated(primary, created, context);
    if (this instanceof ExplicitIDHandler) {
      handleExplicitIDStoring(primary, created, context);
    }
  }

  @Override
  public void delete(P primary, Context<P> context) {
    if (isExplicitIDHandler && !isBulkDependentResource) {
      var secondary = getSecondaryResource(primary, context);
      super.delete(primary, context);
      // deletes the state after the resource is deleted
      handleExplicitIDDelete(primary, secondary.orElse(null), context);
    } else {
      super.delete(primary, context);
    }
  }

  private void handleExplicitIDDelete(P primary, R secondary, Context<P> context) {
    var res = explicitIDHandler.stateResource(primary, secondary);
    explicitIDHandler.getKubernetesClient().resource(res).delete();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void handleExplicitIDStoring(P primary, R created, Context<P> context) {
    HasMetadata resource = explicitIDHandler.stateResource(primary, created);
    var stateResource = explicitIDHandler.getKubernetesClient().resource(resource).create();
    if (externalStateEventSource instanceof RecentOperationCacheFiller) {
      ((RecentOperationCacheFiller) externalStateEventSource)
          .handleRecentResourceCreate(ResourceID.fromResource(primary), stateResource);
    }
  }

  protected InformerEventSource getExternalStateEventSource() {
    return externalStateEventSource;
  }

  // TODO what with this?
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }
}
