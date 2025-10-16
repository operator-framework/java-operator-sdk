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
package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

/**
 * Polls the supplier for each controlled resource registered. Resource is registered when created
 * if there is no registerPredicate provided. If register predicate provided it is evaluated on
 * resource create and/or update to register polling for the event source.
 *
 * <p>For other behavior see {@link ExternalResourceCachingEventSource}
 *
 * @param <R> the resource polled by the event source
 * @param <P> related custom resource
 */
public class PerResourcePollingEventSource<R, P extends HasMetadata, ID>
    extends ExternalResourceCachingEventSource<R, P, ID> implements ResourceEventAware<P> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  private final Map<ResourceID, ScheduledFuture<Void>> scheduledFutures = new ConcurrentHashMap<>();
  private final Cache<P> primaryResourceCache;
  private final Set<ResourceID> fetchedForPrimaries = ConcurrentHashMap.newKeySet();

  private final ScheduledExecutorService executorService;
  private final ResourceFetcher<R, P> resourceFetcher;
  private final Predicate<P> registerPredicate;
  private final Duration period;

  public PerResourcePollingEventSource(
      Class<R> resourceClass,
      EventSourceContext<P> context,
      PerResourcePollingConfiguration<R, P, ID> config) {
    super(config.name(), resourceClass, config.resourceKeyMapper());
    this.primaryResourceCache = context.getPrimaryCache();
    this.resourceFetcher = config.resourceFetcher();
    this.registerPredicate = config.registerPredicate();
    this.executorService = config.executorService();
    this.period = config.defaultPollingPeriod();
  }

  private Set<R> getAndCacheResource(P primary, boolean fromGetter) {
    var values = resourceFetcher.fetchResources(primary);
    handleResources(ResourceID.fromResource(primary), values, !fromGetter);
    fetchedForPrimaries.add(ResourceID.fromResource(primary));
    return values;
  }

  @SuppressWarnings("unchecked")
  private void scheduleNextExecution(P primary, Set<R> actualResources) {
    var primaryID = ResourceID.fromResource(primary);
    var fetchDelay = resourceFetcher.fetchDelay(actualResources, primary);
    var fetchDuration = fetchDelay.orElse(period);

    ScheduledFuture<Void> scheduledFuture =
        (ScheduledFuture<Void>)
            executorService.schedule(
                new FetchingExecutor(primaryID), fetchDuration.toMillis(), TimeUnit.MILLISECONDS);
    scheduledFutures.put(primaryID, scheduledFuture);
  }

  @Override
  public void onResourceCreated(P resource) {
    checkAndRegisterTask(resource);
  }

  @Override
  public void onResourceUpdated(P newResource, P oldResource) {
    checkAndRegisterTask(newResource);
  }

  @Override
  public void onResourceDeleted(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    var scheduledFuture = scheduledFutures.remove(resourceID);
    if (scheduledFuture != null) {
      log.debug("Canceling scheduledFuture for resource: {}", resource);
      scheduledFuture.cancel(true);
    }
    handleDelete(resourceID);
    fetchedForPrimaries.remove(resourceID);
  }

  // This method is always called from the same Thread for the same resource,
  // since events from ResourceEventAware are propagated from the thread of the informer. This is
  // important because otherwise there will be a race condition related to the timerTasks.
  private void checkAndRegisterTask(P resource) {
    var primaryID = ResourceID.fromResource(resource);
    if (scheduledFutures.get(primaryID) == null
        && (registerPredicate == null || registerPredicate.test(resource))) {
      var cachedResources = cache.get(primaryID);
      var actualResources =
          cachedResources == null ? null : new HashSet<>(cachedResources.values());
      // note that there is a delay, to not do two fetches when the resources first appeared
      // and getSecondaryResource is called on reconciliation.
      scheduleNextExecution(resource, actualResources);
    }
  }

  private class FetchingExecutor implements Runnable {
    private final ResourceID primaryID;

    public FetchingExecutor(ResourceID primaryID) {
      this.primaryID = primaryID;
    }

    @Override
    public void run() {
      if (!isRunning()) {
        log.debug("Event source not yet started. Will not run for: {}", primaryID);
        return;
      }
      // always use up-to-date resource from cache
      var primary = primaryResourceCache.get(primaryID);
      if (primary.isEmpty()) {
        log.warn("No resource in cache for resource ID: {}", primaryID);
        // no new execution is scheduled in this case, an on delete event should be received shortly
      } else {
        var actualResources = primary.map(p -> getAndCacheResource(p, false));
        scheduleNextExecution(primary.get(), actualResources.orElse(null));
      }
    }
  }

  /**
   * When this event source is queried for the resource, it might not be fully "synced". Thus, the
   * cache might not be propagated, therefore the supplier is checked for the resource too.
   *
   * @param primary resource of the controller
   * @return the related resource for this event source
   */
  @Override
  public Set<R> getSecondaryResources(P primary) {
    var primaryID = ResourceID.fromResource(primary);
    var cachedValue = cache.get(primaryID);
    if (cachedValue != null && !cachedValue.isEmpty()) {
      return new HashSet<>(cachedValue.values());
    } else {
      if (fetchedForPrimaries.contains(primaryID)) {
        return Collections.emptySet();
      } else {
        return getAndCacheResource(primary, true);
      }
    }
  }

  public interface ResourceFetcher<R, P> {
    Set<R> fetchResources(P primaryResource);

    /**
     * By implementing this method it is possible to specify dynamic durations to wait between the
     * polls of the resources. This is especially handy if a resources "stabilized" so it is not
     * expected to change its state frequently. For example an AWS RDS instance is up and running,
     * it is expected to run and be stable for a very long time. In this case it is enough to poll
     * with a lower frequency, compared to the phase when it is being initialized.
     *
     * @param lastFetchedResource might be null, in case no fetch happened before. Empty set if
     *     fetch happened but no resources were found.
     * @param primary related primary resource
     * @return an Optional containing the Duration to wait until the next fetch. If an empty
     *     Optional is returned, the default polling period will be used.
     */
    default Optional<Duration> fetchDelay(Set<R> lastFetchedResource, P primary) {
      return Optional.empty();
    }
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    executorService.shutdownNow();
  }
}
