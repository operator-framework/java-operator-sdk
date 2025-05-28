package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.Objects;

import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public record PollingConfiguration<R>(
    String name,
    PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
    Duration period,
    CacheKeyMapper<R> cacheKeyMapper) {

  public PollingConfiguration(
      String name,
      PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher,
      Duration period,
      CacheKeyMapper<R> cacheKeyMapper) {
    this.name = name;
    this.genericResourceFetcher = Objects.requireNonNull(genericResourceFetcher);
    this.period = period;
    this.cacheKeyMapper =
        cacheKeyMapper == null ? CacheKeyMapper.singleResourceCacheKeyMapper() : cacheKeyMapper;
  }
}
