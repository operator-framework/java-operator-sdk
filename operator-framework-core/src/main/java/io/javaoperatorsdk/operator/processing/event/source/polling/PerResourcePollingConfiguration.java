package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;

public record PerResourcePollingConfiguration<R, P extends HasMetadata>(ScheduledExecutorService executorService, CacheKeyMapper<R> cacheKeyMapper,
                                                                        PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher,
                                                                        Predicate<P> registerPredicate, Duration defaultPollingPeriod,
                                                                        Class<R> resourceClass) {

    public static final int DEFAULT_EXECUTOR_THREAD_NUMBER = 1;

    public PerResourcePollingConfiguration(ScheduledExecutorService executorService,
                                           CacheKeyMapper<R> cacheKeyMapper,
                                           PerResourcePollingEventSource.ResourceFetcher<R, P> resourceFetcher,
                                           Predicate<P> registerPredicate,
                                           Duration defaultPollingPeriod, Class<R> resourceClass) {
        this.executorService = executorService == null ? new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREAD_NUMBER)
                : executorService;
        this.cacheKeyMapper = cacheKeyMapper;
        this.resourceFetcher = resourceFetcher;
        this.registerPredicate = registerPredicate;
        this.defaultPollingPeriod = defaultPollingPeriod;
        this.resourceClass = resourceClass;

    }
}
