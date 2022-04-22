package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

public abstract class PollingDependentResource<R, P extends HasMetadata>
    extends AbstractPollingDependentResource<R, P>
    implements PollingEventSource.GenericResourceFetcher<R> {

  private final CacheKeyMapper<R> idProvider;

  public PollingDependentResource(Class<R> resourceType, CacheKeyMapper<R> idProvider) {
    super(resourceType);
    this.idProvider = idProvider;
  }

  public PollingDependentResource(Class<R> resourceType, long pollingPeriod,
      CacheKeyMapper<R> idProvider) {
    super(resourceType, pollingPeriod);
    this.idProvider = idProvider;
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P> createEventSource(
      EventSourceContext<P> context) {
    return new PollingEventSource<>(this, getPollingPeriod(), resourceType(), idProvider);
  }

}
