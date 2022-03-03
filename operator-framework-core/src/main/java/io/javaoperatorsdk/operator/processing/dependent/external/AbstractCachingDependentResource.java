package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public abstract class AbstractCachingDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P> implements EventSourceProvider<P> {

  protected ExternalResourceCachingEventSource<R, P> eventSource;

  public Optional<R> getSupplierResource(P primaryResource) {
    return eventSource.getAssociated(primaryResource);
  }

  @Override
  public EventSource getEventSource() {
    return eventSource;
  }

  protected Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromExtendedClass(getClass());
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return eventSource.getAssociated(primaryResource);
  }
}
