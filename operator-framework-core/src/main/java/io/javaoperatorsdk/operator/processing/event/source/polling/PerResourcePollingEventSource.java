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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

/**
 *
 * Polls the supplier for each controlled resource registered. Resource is registered when created
 * if there is no registerPredicate provided. If register predicate provided it is evaluated on
 * resource create and/or update to register polling for the event source.
 * <p>
 * For other behavior see {@link ExternalResourceCachingEventSource}
 *
 * @param <R> the resource polled by the event source
 * @param <P> related custom resource
 */
public class PerResourcePollingEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P>
    implements ResourceEventAware<P> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  public static final int DEFAULT_EXECUTOR_THREAD_NUMBER = 1;

  private final ScheduledExecutorService executorService;
  private final Map<ResourceID, ScheduledFuture<Void>> scheduledFutures = new ConcurrentHashMap<>();
  private final ResourceFetcher<R, P> resourceFetcher;
  private final Cache<P> resourceCache;
  private final Predicate<P> registerPredicate;
  private final long period;
  private final Set<ResourceID> fetchedForPrimaries = ConcurrentHashMap.newKeySet();

  // todo naming

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      EventSourceContext<P> context, Duration defaultPollingPeriod,
      Class<R> resourceClass) {
    this(resourceFetcher, context.getPrimaryCache(), defaultPollingPeriod.toMillis(),
        null, resourceClass,
        CacheKeyMapper.singleResourceCacheKeyMapper());
  }

  /**
   * @deprecated use the variant which uses {@link EventSourceContext} instead of {@link Cache} and
   *             {@link Duration} for period parameter as it provides a more intuitive API.
   *
   * @param resourceFetcher fetches resource related to a primary resource
   * @param resourceCache cache of the primary resource
   * @param period default polling period
   * @param resourceClass class of the target resource
   */
  @Deprecated(forRemoval = true)
  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period, Class<R> resourceClass) {
    this(resourceFetcher, resourceCache, period, null, resourceClass,
        CacheKeyMapper.singleResourceCacheKeyMapper());
  }

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      EventSourceContext<P> context,
      Duration defaultPollingPeriod,
      Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    this(resourceFetcher, context.getPrimaryCache(), defaultPollingPeriod.toMillis(),
        null, resourceClass, cacheKeyMapper);
  }

  /**
   * @deprecated use the variant which uses {@link EventSourceContext} instead of {@link Cache} and
   *             {@link Duration} for period parameter as it provides a more intuitive API.
   *
   * @param resourceFetcher fetches resource related to a primary resource
   * @param resourceCache cache of the primary resource
   * @param period default polling period
   * @param resourceClass class of the target resource
   * @param cacheKeyMapper use to distinguish resource in case more resources are handled for a
   *        single primary resource
   */
  @Deprecated(forRemoval = true)
  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    this(resourceFetcher, resourceCache, period, null, resourceClass, cacheKeyMapper);
  }

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      EventSourceContext<P> context,
      Duration defaultPollingPeriod,
      Predicate<P> registerPredicate,
      Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    this(resourceFetcher, context.getPrimaryCache(), defaultPollingPeriod.toMillis(),
        registerPredicate, resourceClass, cacheKeyMapper,
        new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREAD_NUMBER));
  }

  /**
   * @deprecated use the variant which uses {@link EventSourceContext} instead of {@link Cache} and
   *             {@link Duration} for period parameter as it provides a more intuitive API.
   *
   * @param resourceFetcher fetches resource related to a primary resource
   * @param resourceCache cache of the primary resource
   * @param period default polling period
   * @param resourceClass class of the target resource
   * @param cacheKeyMapper use to distinguish resource in case more resources are handled for a
   *        single primary resource
   * @param registerPredicate used to determine if the related resource for a custom resource should
   *        be polled or not.
   */
  @Deprecated(forRemoval = true)
  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period,
      Predicate<P> registerPredicate, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    this(resourceFetcher, resourceCache, period, registerPredicate, resourceClass, cacheKeyMapper,
        new ScheduledThreadPoolExecutor(DEFAULT_EXECUTOR_THREAD_NUMBER));
  }


  public PerResourcePollingEventSource(
      ResourceFetcher<R, P> resourceFetcher,
      EventSourceContext<P> context, Duration defaultPollingPeriod,
      Predicate<P> registerPredicate, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper, ScheduledExecutorService executorService) {
    this(resourceFetcher, context.getPrimaryCache(), defaultPollingPeriod.toMillis(),
        registerPredicate,
        resourceClass, cacheKeyMapper, executorService);
  }

  /**
   * @deprecated use the variant which uses {@link EventSourceContext} instead of {@link Cache} and
   *             {@link Duration} for period parameter as it provides a more intuitive API.
   *
   * @param resourceFetcher fetches resource related to a primary resource
   * @param resourceCache cache of the primary resource
   * @param period default polling period
   * @param resourceClass class of the target resource
   * @param cacheKeyMapper use to distinguish resource in case more resources are handled for a
   *        single primary resource
   * @param registerPredicate used to determine if the related resource for a custom resource should
   *        be polled or not.
   * @param executorService custom executor service
   */

  @Deprecated(forRemoval = true)
  public PerResourcePollingEventSource(
      ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period,
      Predicate<P> registerPredicate, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper, ScheduledExecutorService executorService) {
    super(resourceClass, cacheKeyMapper);
    this.resourceFetcher = resourceFetcher;
    this.resourceCache = resourceCache;
    this.period = period;
    this.registerPredicate = registerPredicate;
    this.executorService = executorService;
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
    var fetchDuration = fetchDelay.orElse(Duration.ofMillis(period));

    ScheduledFuture<Void> scheduledFuture = (ScheduledFuture<Void>) executorService
        .schedule(new FetchingExecutor(primaryID), fetchDuration.toMillis(), TimeUnit.MILLISECONDS);
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
    if (scheduledFutures.get(primaryID) == null && (registerPredicate == null
        || registerPredicate.test(resource))) {
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
      var primary = resourceCache.get(primaryID);
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
     *        fetch happened but no resources were found.
     * @param primary related primary resource
     * @return an Optional containing the Duration to wait until the next fetch. If an empty
     *         Optional is returned, the default polling period will be used.
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
