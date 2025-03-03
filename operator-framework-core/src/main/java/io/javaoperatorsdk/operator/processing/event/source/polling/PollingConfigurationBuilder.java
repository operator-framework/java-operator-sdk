package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;

import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public final class PollingConfigurationBuilder<R> {
  private final Duration period;
  private final PollingEventSource.GenericResourceFetcher<R> genericResourceFetcher;
  private CacheKeyMapper<R> cacheKeyMapper;
  private String name;

  public PollingConfigurationBuilder(
      PollingEventSource.GenericResourceFetcher<R> fetcher, Duration period) {
    this.genericResourceFetcher = fetcher;
    this.period = period;
  }

  public PollingConfigurationBuilder<R> withCacheKeyMapper(CacheKeyMapper<R> cacheKeyMapper) {
    this.cacheKeyMapper = cacheKeyMapper;
    return this;
  }

  public PollingConfigurationBuilder<R> withName(String name) {
    this.name = name;
    return this;
  }

  public PollingConfiguration<R> build() {
    return new PollingConfiguration<>(name, genericResourceFetcher, period, cacheKeyMapper);
  }
}
