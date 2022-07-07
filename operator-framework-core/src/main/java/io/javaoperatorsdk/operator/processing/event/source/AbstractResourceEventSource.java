package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;

public abstract class AbstractResourceEventSource<R, P extends HasMetadata>
    extends AbstractEventSource
    implements ResourceEventSource<R, P> {
  private final Class<R> resourceClass;

  protected EventFilter<R> filter = EventFilter.ACCEPTS_ALL;

  protected AbstractResourceEventSource(Class<R> resourceClass) {
    this.resourceClass = resourceClass;
  }

  @Override
  public Class<R> resourceType() {
    return resourceClass;
  }

  @Override
  public void setFilter(EventFilter<R> filter) {
    this.filter = filter == null ? EventFilter.ACCEPTS_ALL : filter;
  }
}
