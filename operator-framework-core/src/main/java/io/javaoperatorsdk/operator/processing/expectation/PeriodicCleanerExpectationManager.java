package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.time.LocalDateTime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.IndexedResourceCache;

public class PeriodicCleanerExpectationManager<P extends HasMetadata>
    extends ExpectationManager<P> {

  private final Duration cleanupDelayAfterExpiration;
  private final IndexedResourceCache<P> primaryCache;

  // todo fixes schedule
  public PeriodicCleanerExpectationManager(Duration period, Duration cleanupDelayAfterExpiration) {
    this.cleanupDelayAfterExpiration = cleanupDelayAfterExpiration;
    this.primaryCache = null;
  }

  public PeriodicCleanerExpectationManager(Duration period, IndexedResourceCache<P> primaryCache) {
    this.cleanupDelayAfterExpiration = null;
    this.primaryCache = primaryCache;
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
}
