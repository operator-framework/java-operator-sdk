package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ResourceTypeAware;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public abstract class AbstractCachingDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P>
    implements EventSourceProvider<P>, ResourceTypeAware<R> {

  protected ExternalResourceCachingEventSource<R, P> eventSource;
  private final Class<R> resourceType;

  protected AbstractCachingDependentResource(Class<R> resourceType) {
    this.resourceType = resourceType;
  }

  public Optional<R> fetchResource(P primaryResource) {
    return eventSource.getAssociated(primaryResource);
  }

  @Override
  public EventSource getEventSource() {
    return eventSource;
  }

  @Override
  public Class<R> resourceType() {
    return resourceType;
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return eventSource.getAssociated(primaryResource);
  }
}
