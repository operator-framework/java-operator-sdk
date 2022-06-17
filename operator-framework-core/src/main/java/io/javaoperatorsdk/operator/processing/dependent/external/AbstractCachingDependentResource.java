package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.dependent.AbstractEventSourceHolderDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

@Ignore
public abstract class AbstractCachingDependentResource<R, P extends HasMetadata>
    extends
    AbstractEventSourceHolderDependentResource<R, P, ExternalResourceCachingEventSource<R, P>> {
  private final Class<R> resourceType;

  protected AbstractCachingDependentResource(Class<R> resourceType) {
    this.resourceType = resourceType;
  }

  @Override
  public Class<R> resourceType() {
    return resourceType;
  }

  @Override
  public Optional<R> getSecondaryResource(P primaryResource) {
    return eventSource().getSecondaryResource(primaryResource);
  }
}
