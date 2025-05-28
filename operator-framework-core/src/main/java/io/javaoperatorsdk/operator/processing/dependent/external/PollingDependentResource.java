package io.javaoperatorsdk.operator.processing.dependent.external;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

@Ignore
public abstract class PollingDependentResource<R, P extends HasMetadata>
    extends AbstractPollingDependentResource<R, P>
    implements PollingEventSource.GenericResourceFetcher<R> {

  private final CacheKeyMapper<R> cacheKeyMapper;

  public PollingDependentResource(Class<R> resourceType, CacheKeyMapper<R> cacheKeyMapper) {
    super(resourceType);
    this.cacheKeyMapper = cacheKeyMapper;
  }

  public PollingDependentResource(
      Class<R> resourceType, Duration pollingPeriod, CacheKeyMapper<R> cacheKeyMapper) {
    super(resourceType, pollingPeriod);
    this.cacheKeyMapper = cacheKeyMapper;
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P> createEventSource(
      EventSourceContext<P> context) {
    return new PollingEventSource<>(
        resourceType(),
        new PollingConfiguration<>(name(), this, getPollingPeriod(), cacheKeyMapper));
  }
}
