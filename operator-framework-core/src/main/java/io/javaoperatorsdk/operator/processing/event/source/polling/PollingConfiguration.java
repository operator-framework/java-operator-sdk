package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;

import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public record PollingConfiguration<R>(Class<R> resourceClass,
                                      PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
                                      Duration period, CacheKeyMapper<R> cacheKeyMapper) {

  public PollingConfiguration(Class<R> resourceClass,
                              PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher, Duration period,
                              CacheKeyMapper<R> cacheKeyMapper) {
    this.resourceClass = resourceClass;
    this.genericResourceFetcher = genericResourceFetcher;
    this.period = period;
    this.cacheKeyMapper =
            cacheKeyMapper == null ? CacheKeyMapper.singleResourceCacheKeyMapper() : cacheKeyMapper;
  }
}
