package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public final class PerResourcePollingConfigurationBuilder<R, P extends HasMetadata> {

  private final Duration defaultPollingPeriod;
  private final Class<R> resourceClass;
  private final PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher;

  private Predicate<P> registerPredicate;
  private ScheduledExecutorService executorService;
  private CacheKeyMapper<R> cacheKeyMapper;

  public PerResourcePollingConfigurationBuilder(Class<R> resourceClass,
      PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher,
      Duration defaultPollingPeriod) {
    this.resourceClass = resourceClass;
    this.resourceFetcher = resourceFetcher;
    this.defaultPollingPeriod = defaultPollingPeriod;
  }

  public PerResourcePollingConfigurationBuilder<R, P> withExecutorService(
      ScheduledExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  public PerResourcePollingConfigurationBuilder<R, P> withRegisterPredicate(
      Predicate<P> registerPredicate) {
    this.registerPredicate = registerPredicate;
    return this;
  }

  public PerResourcePollingConfigurationBuilder<R, P> withCacheKeyMapper(
      CacheKeyMapper<R> cacheKeyMapper) {
    this.cacheKeyMapper = cacheKeyMapper;
    return this;
  }


  public PerResourcePollingConfiguration<R, P> build() {
    return new PerResourcePollingConfiguration<>(executorService, cacheKeyMapper,
        resourceFetcher, registerPredicate, defaultPollingPeriod, resourceClass);
  }
}
