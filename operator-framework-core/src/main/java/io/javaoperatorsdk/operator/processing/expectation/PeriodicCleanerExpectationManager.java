package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.IndexedResourceCache;

public class PeriodicCleanerExpectationManager<P extends HasMetadata>
    extends ExpectationManager<P> {

  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          1,
          r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            return thread;
          });

  private final Duration cleanupDelayAfterExpiration;
  private final IndexedResourceCache<P> primaryCache;

  public PeriodicCleanerExpectationManager(Duration period, Duration cleanupDelayAfterExpiration) {
    this(period, cleanupDelayAfterExpiration, null);
  }

  public PeriodicCleanerExpectationManager(Duration period, IndexedResourceCache<P> primaryCache) {
    this(period, null, primaryCache);
  }

  private PeriodicCleanerExpectationManager(
      Duration period, Duration cleanupDelayAfterExpiration, IndexedResourceCache<P> primaryCache) {
    this.cleanupDelayAfterExpiration = cleanupDelayAfterExpiration;
    this.primaryCache = primaryCache;
    scheduler.scheduleWithFixedDelay(
        this::clean, period.toMillis(), period.toMillis(), TimeUnit.MICROSECONDS);
  }

  public void clean() {
    registeredExpectations
        .entrySet()
        .removeIf(
            e -> {
              if (cleanupDelayAfterExpiration != null) {
                return LocalDateTime.now()
                    .isAfter(
                        e.getValue()
                            .registeredAt()
                            .plus(e.getValue().timeout())
                            .plus(cleanupDelayAfterExpiration));
              } else {
                return primaryCache.get(e.getKey()).isEmpty();
              }
            });
  }

  void stop() {
    scheduler.shutdownNow();
  }
}
