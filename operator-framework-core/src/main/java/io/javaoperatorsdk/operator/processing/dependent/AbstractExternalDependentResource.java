package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

import java.util.Optional;

public abstract class AbstractExternalDependentResource<R, P extends HasMetadata, T extends ResourceEventSource<R, P>>
    extends AbstractEventSourceHolderDependentResource<R, P, T> {

  protected AbstractExternalDependentResource(Class<R> resourceType) {
    super(resourceType);
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
    if (this instanceof ExplicitIDHandler) {
      var secondary = getSecondaryResource(primary,context);
      super.delete(primary, context);
      // deletes the state after the resource is deleted
      handleExplicitIDDelete(primary,secondary.orElse(null),context);
    } else {
      super.delete(primary, context);
    }

  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void handleExplicitIDDelete(P primary, R secondary, Context<P> context) {
    ExplicitIDHandler<R, P, ?> handler = (ExplicitIDHandler) this;
    var res = handler.stateResource(primary,secondary);
    handler.getKubernetesClient().resource(res).delete();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void handleExplicitIDStoring(P primary, R created, Context<P> context) {
    ExplicitIDHandler<R, P, ?> handler = (ExplicitIDHandler) this;
    HasMetadata resource = handler.stateResource(primary, created);
    var stateResource = handler.getKubernetesClient().resource(resource).create();
    var eventSource = handler.eventSourceName()
        .map(n -> context.eventSourceRetriever()
            .getResourceEventSourceFor((Class<R>) resource.getClass(), n))
        .orElseGet(() -> context.eventSourceRetriever()
            .getResourceEventSourceFor((Class<R>) resource.getClass()));
    if (eventSource instanceof RecentOperationCacheFiller) {
      ((RecentOperationCacheFiller) eventSource)
          .handleRecentResourceCreate(ResourceID.fromResource(primary), stateResource);
    }
  }

}
