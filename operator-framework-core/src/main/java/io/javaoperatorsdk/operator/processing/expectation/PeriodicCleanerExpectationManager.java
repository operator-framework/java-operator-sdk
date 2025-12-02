/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;
import io.javaoperatorsdk.operator.api.reconciler.IndexedResourceCache;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Expectation manager implementation that works without enabling {@link
 * ControllerConfiguration#triggerReconcilerOnAllEvents()}. Periodically checks and cleans
 * expectations for primary resources which are no longer present in the cache.
 */
@Experimental(API_MIGHT_CHANGE)
public class PeriodicCleanerExpectationManager<P extends HasMetadata>
    extends ExpectationManager<P> {

  public static final Duration DEFAULT_CHECK_PERIOD = Duration.ofMinutes(1);

  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          1,
          r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            return thread;
          });

  private final IndexedResourceCache<P> primaryCache;

  public PeriodicCleanerExpectationManager(IndexedResourceCache<P> primaryCache) {
    this(DEFAULT_CHECK_PERIOD, primaryCache);
  }

  public PeriodicCleanerExpectationManager(Duration period, IndexedResourceCache<P> primaryCache) {
    this.primaryCache = primaryCache;
    scheduler.scheduleWithFixedDelay(
        this::clean, period.toMillis(), period.toMillis(), TimeUnit.MICROSECONDS);
  }

  protected void clean() {
    registeredExpectations.entrySet().removeIf(e -> primaryCache.get(e.getKey()).isEmpty());
  }

  /** Allows to stop manager. Note that you usually don't have to stop the manager explicitly. */
  public void stop() {
    scheduler.shutdownNow();
  }
}
