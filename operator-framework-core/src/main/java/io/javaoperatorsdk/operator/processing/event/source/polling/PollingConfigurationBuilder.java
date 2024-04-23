package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;

import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public final class PollingConfigurationBuilder<R> {
  private final Class<R> resourceClass;
  private final PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher;
  private final Duration period;
  private CacheKeyMapper<R> cacheKeyMapper;

  public PollingConfigurationBuilder(Class<R> resourceClass,
      PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
      Duration period) {
    this.resourceClass = resourceClass;
    this.genericResourceFetcher = genericResourceFetcher;
    this.period = period;
  }

  public PollingConfigurationBuilder<R> withCacheKeyMapper(CacheKeyMapper<R> cacheKeyMapper) {
    this.cacheKeyMapper = cacheKeyMapper;
    return this;
  }

  public PollingConfiguration<R> build() {
    return new PollingConfiguration<>(resourceClass, genericResourceFetcher, period,
        cacheKeyMapper);
  }
}
