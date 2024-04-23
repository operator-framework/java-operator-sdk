package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;

import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public class PollingConfiguration<R> {

  private final Class<R> resourceClass;
  private final PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher;
  private final Duration period;
  private final CacheKeyMapper<R> cacheKeyMapper;

  public PollingConfiguration(Class<R> resourceClass,
      PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher, Duration period,
      CacheKeyMapper<R> cacheKeyMapper) {
    this.resourceClass = resourceClass;
    this.genericResourceFetcher = genericResourceFetcher;
    this.period = period;
    this.cacheKeyMapper =
        cacheKeyMapper == null ? CacheKeyMapper.singleResourceCacheKeyMapper() : cacheKeyMapper;
  }

  public Class<R> getResourceClass() {
    return resourceClass;
  }

  public PollingEventSource.GenericResourceFetcher<R> getGenericResourceFetcher() {
    return genericResourceFetcher;
  }

  public Duration getPeriod() {
    return period;
  }

  public CacheKeyMapper<R> getCacheKeyMapper() {
    return cacheKeyMapper;
  }
}
