package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

/**
 *
 * Polls the supplier for each controlled resource registered. Resource is registered when created
 * if there is no registerPredicate provided. If register predicate provided it is evaluated on
 * resource create and/or update to register polling for the event source.
 * <p>
 * For other behavior see {@link CachingEventSource}
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

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period, Class<R> resourceClass) {
    this(resourceFetcher, resourceCache, period, null, resourceClass);
  }

  public PerResourcePollingEventSource(ResourceFetcher<R, P> resourceFetcher,
      Cache<P> resourceCache, long period,
      Predicate<P> registerPredicate, Class<R> resourceClass) {
    super(resourceClass);
    this.resourceFetcher = resourceFetcher;
    this.resourceCache = resourceCache;
    this.period = period;
    this.registerPredicate = registerPredicate;
  }

  private void pollForResource(P resource) {
    var value = resourceFetcher.fetchResource(resource);
    var resourceID = ResourceID.fromResource(resource);
    if (value.isEmpty()) {
      super.handleDelete(resourceID);
    } else {
      super.handleEvent(value.get(), resourceID);
    }
  }

  private Optional<R> getAndCacheResource(ResourceID resourceID) {
    var resource = resourceCache.get(resourceID);
    if (resource.isPresent()) {
      var value = resourceFetcher.fetchResource(resource.get());
      value.ifPresent(v -> cache.put(resourceID, v));
      return value;
    }
    return Optional.empty();
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
    cache.remove(resourceID);
  }

  // This method is always called from the same Thread for the same resource,
  // since events from ResourceEventAware are propagated from the thread of the informer. This is
  // important
  // because otherwise there will be a race condition related to the timerTasks.
  private void checkAndRegisterTask(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    if (timerTasks.get(resourceID) == null && (registerPredicate == null
        || registerPredicate.test(resource))) {
      var task = new TimerTask() {
        @Override
        public void run() {
          if (!isRunning()) {
            log.debug("Event source not yet started. Will not run for: {}", resourceID);
            return;
          }
          // always use up-to-date resource from cache
          var res = resourceCache.get(resourceID);
          res.ifPresentOrElse(r -> pollForResource(r),
              () -> log.warn("No resource in cache for resource ID: {}", resourceID));
        }
      };
      timerTasks.put(resourceID, task);
      timer.schedule(task, 0, period);
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
  public Optional<R> getAssociatedResource(P primary) {
    return getValueFromCacheOrSupplier(ResourceID.fromResource(primary));
  }

  /**
   *
   * @param resourceID of the target related resource
   * @return the cached value of the resource, if not present it gets the resource from the
   *         supplier. The value provided from the supplier is cached, but no new event is
   *         propagated.
   */
  public Optional<R> getValueFromCacheOrSupplier(ResourceID resourceID) {
    var cachedValue = getCachedValue(resourceID);
    if (cachedValue.isPresent()) {
      return cachedValue;
    } else {
      return getAndCacheResource(resourceID);
    }
  }

  public interface ResourceFetcher<R, P> {
    Optional<R> fetchResource(P primaryResource);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }
}
