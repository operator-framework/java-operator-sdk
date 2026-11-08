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

  private final ScheduledExecutorService configuredExecutorService;
  private final boolean useVirtualThreads;
  private final boolean ownsExecutorService;
  private final ResourceFetcher<R, P> resourceFetcher;
  private final Predicate<P> registerPredicate;
  private final Duration period;

  private volatile ScheduledExecutorService executorService;

  // incremented on every stop, to tell the polls of previous runs apart, which can outlive the
  // stop when the fetcher ignores the interruption; guarded by this
  private long lifecycleGeneration;

  public PerResourcePollingEventSource(
      Class<R> resourceClass,
      EventSourceContext<P> context,
      PerResourcePollingConfiguration<R, P, ID> config) {
    super(config.name(), resourceClass, config.resourceIDMapper());
    this.primaryResourceCache = context.getPrimaryCache();
    this.resourceFetcher = config.resourceFetcher();
    this.registerPredicate = config.registerPredicate();
    this.configuredExecutorService = config.executorService();
    this.useVirtualThreads = PollingExecutors.useVirtualThreads(context);
    this.ownsExecutorService = configuredExecutorService == null;
    this.period = config.defaultPollingPeriod();
  }

  @Override
  public void start() throws OperatorException {
    if (isRunning()) {
      return;
    }
    executorService = resolveExecutorService();
    super.start();
    // the primary informer is started before this event source, and the executor to poll on is
    // only resolved when this one starts, so the resources seen in the meantime are registered now
    primaryResourceCache.list().forEach(this::checkAndRegisterTask);
  }

  /**
   * Resolves the executor to poll on. Note that this happens on every start, since an executor
   * created by this event source is shut down when it is stopped.
   */
  private ScheduledExecutorService resolveExecutorService() {
    if (configuredExecutorService != null) {
      return configuredExecutorService;
    }
    return PollingExecutors.newExecutor(name(), useVirtualThreads);
  }

  private Set<R> getAndCacheResource(P primary, boolean fromGetter) {
    var values = resourceFetcher.fetchResources(primary);
    cacheResources(primary, values, fromGetter);
    return values;
  }

  private void cacheResources(P primary, Set<R> values, boolean fromGetter) {
    var primaryID = ResourceID.fromResource(primary);
    handleResources(primaryID, values, !fromGetter);
    fetchedForPrimaries.add(primaryID);
  }

  // the polling of a resource that is gone ends with its last task, which would otherwise stay
  // registered and keep the resource from being polled again if it is recreated
  private synchronized void forgetTask(ResourceID primaryID, long generation) {
    if (generation == lifecycleGeneration) {
      scheduledFutures.remove(primaryID);
    }
  }

  // a fetch ignoring the interruption of a stop can return after a restart, its result is not to
  // overwrite what the polls of the new run cached in the meantime
  private synchronized void cacheAndScheduleNextExecution(
      P primary, Set<R> values, long generation) {
    if (generation != lifecycleGeneration) {
      return;
    }
    cacheResources(primary, values, false);
    scheduleNextExecution(primary, values, generation);
  }

  @SuppressWarnings("unchecked")
  private synchronized void scheduleNextExecution(
      P primary, Set<R> actualResources, long generation) {
    if (generation != lifecycleGeneration) {
      return;
    }
    var primaryID = ResourceID.fromResource(primary);
    var fetchDelay = resourceFetcher.fetchDelay(actualResources, primary);
    var fetchDuration = fetchDelay.orElse(period);

    ScheduledFuture<Void> scheduledFuture =
        (ScheduledFuture<Void>)
            executorService.schedule(
                new FetchingExecutor(primaryID, generation),
                fetchDuration.toMillis(),
                TimeUnit.MILLISECONDS);
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
  public synchronized void onResourceDeleted(P resource) {
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
  private synchronized void checkAndRegisterTask(P resource) {
    // stop() clears the running flag before it takes the monitor, so a registration that raced it
    // is dropped, otherwise a task scheduled after the stop would block a restart
    if (!isRunning()) {
      return;
    }
    var primaryID = ResourceID.fromResource(resource);
    if (scheduledFutures.get(primaryID) == null
        && (registerPredicate == null || registerPredicate.test(resource))) {
      var cachedResources = cachedResourcesFor(primaryID);
      var actualResources = cachedResources.isEmpty() ? null : cachedResources;
      // note that there is a delay, to not do two fetches when the resources first appeared
      // and getSecondaryResource is called on reconciliation.
      scheduleNextExecution(resource, actualResources, lifecycleGeneration);
    }
  }

  private class FetchingExecutor implements Runnable {
    private final ResourceID primaryID;
    private final long generation;

    public FetchingExecutor(ResourceID primaryID, long generation) {
      this.primaryID = primaryID;
      this.generation = generation;
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
        forgetTask(primaryID, generation);
      } else {
        var values = resourceFetcher.fetchResources(primary.get());
        cacheAndScheduleNextExecution(primary.get(), values, generation);
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
    var cachedValues = cachedResourcesFor(primaryID);
    if (!cachedValues.isEmpty()) {
      return cachedValues;
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
    // the tasks have to be cancelled explicitly since a provided executor is not shut down, and the
    // map cleared so that they are registered again on a restart
    synchronized (this) {
      lifecycleGeneration++;
      scheduledFutures.values().forEach(future -> future.cancel(true));
      scheduledFutures.clear();
    }
    if (ownsExecutorService && executorService != null) {
      executorService.shutdownNow();
    }
  }
}
