package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
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

  private final Timer timer = new Timer();
  private final Map<ResourceID, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceFetcher<R, P> resourceFetcher;
  private final Cache<P> resourceCache;
  private final Predicate<P> registerPredicate;
  private final long period;
  private final Set<ResourceID> fetchedForPrimaries = ConcurrentHashMap.newKeySet();


  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period, Class<R> resourceClass) {
    this(resourceFetcher, resourceCache, period, null, resourceClass,
        CacheKeyMapper.singleResourceCacheKeyMapper());
  }

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    this(resourceFetcher, resourceCache, period, null, resourceClass, cacheKeyMapper);
  }

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period,
      Predicate<P> registerPredicate, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    super(resourceClass, cacheKeyMapper);
    this.resourceFetcher = resourceFetcher;
    this.resourceCache = resourceCache;
    this.period = period;
    this.registerPredicate = registerPredicate;
  }

  private Set<R> getAndCacheResource(P primary, boolean fromGetter) {
    var values = resourceFetcher.fetchResources(primary);
    handleResources(ResourceID.fromResource(primary), values, !fromGetter);
    fetchedForPrimaries.add(ResourceID.fromResource(primary));
    return values;
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
    TimerTask task = timerTasks.remove(resourceID);
    if (task != null) {
      log.debug("Canceling task for resource: {}", resource);
      task.cancel();
    }
    handleDelete(resourceID);
    fetchedForPrimaries.remove(resourceID);
  }

  // This method is always called from the same Thread for the same resource,
  // since events from ResourceEventAware are propagated from the thread of the informer. This is
  // important
  // because otherwise there will be a race condition related to the timerTasks.
  private void checkAndRegisterTask(P resource) {
    var primaryID = ResourceID.fromResource(resource);
    if (timerTasks.get(primaryID) == null && (registerPredicate == null
        || registerPredicate.test(resource))) {
      var task =
          new TimerTask() {
            @Override
            public void run() {
              if (!isRunning()) {
                log.debug("Event source not yet started. Will not run for: {}", primaryID);
                return;
              }
              // always use up-to-date resource from cache
              var res = resourceCache.get(primaryID);
              res.ifPresentOrElse(p -> getAndCacheResource(p, false),
                  () -> log.warn("No resource in cache for resource ID: {}", primaryID));
            }
          };
      timerTasks.put(primaryID, task);
      // there is a delay, to not do two fetches when the resources first appeared
      // and getSecondaryResource is called on reconciliation.
      timer.schedule(task, period, period);
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
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }

}
