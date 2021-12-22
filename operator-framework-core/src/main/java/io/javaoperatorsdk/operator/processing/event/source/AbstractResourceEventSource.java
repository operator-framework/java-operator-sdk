package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;

public abstract class AbstractResourceEventSource<P extends HasMetadata, R>
    extends AbstractEventSource
    implements ResourceEventSource<P, R> {
  private final Class<R> resourceClass;

  protected AbstractResourceEventSource(Class<R> resourceClass) {
    this.resourceClass = resourceClass;
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }
}
