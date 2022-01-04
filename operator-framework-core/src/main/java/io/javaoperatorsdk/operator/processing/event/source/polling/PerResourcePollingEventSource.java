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
 * @param <T> the resource polled by the event source
 * @param <R> related custom resource
 */
public class PerResourcePollingEventSource<T, R extends HasMetadata>
    extends CachingEventSource<T, R>
    implements ResourceEventAware<R> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  private final Timer timer = new Timer();
  private final Map<ResourceID, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceSupplier<T, R> resourceSupplier;
  private final Cache<R> resourceCache;
  private final Predicate<R> registerPredicate;
  private final long period;

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      Cache<R> resourceCache, long period, Class<T> resourceClass) {
    this(resourceSupplier, resourceCache, period, null, resourceClass);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      Cache<R> resourceCache, long period,
      Predicate<R> registerPredicate, Class<T> resourceClass) {
    super(resourceClass);
    this.resourceSupplier = resourceSupplier;
    this.resourceCache = resourceCache;
    this.period = period;
    this.registerPredicate = registerPredicate;
  }

  private void pollForResource(R resource) {
    var value = resourceSupplier.getResources(resource);
    var resourceID = ResourceID.fromResource(resource);
    if (value.isEmpty()) {
      super.handleDelete(resourceID);
    } else {
      super.handleEvent(value.get(), resourceID);
    }
  }

  private Optional<T> getAndCacheResource(ResourceID resourceID) {
    var resource = resourceCache.get(resourceID);
    if (resource.isPresent()) {
      var value = resourceSupplier.getResources(resource.get());
      value.ifPresent(v -> cache.put(resourceID, v));
      return value;
    }
    return Optional.empty();
  }

  @Override
  public void onResourceCreated(R resource) {
    checkAndRegisterTask(resource);
  }

  @Override
  public void onResourceUpdated(R newResource, R oldResource) {
    checkAndRegisterTask(newResource);
  }

  @Override
  public void onResourceDeleted(R resource) {
    var resourceID = ResourceID.fromResource(resource);
    TimerTask task = timerTasks.remove(resourceID);
    if (task != null) {
      log.debug("Canceling task for resource: {}", resource);
      task.cancel();
    }
    cache.remove(resourceID);
  }

  private void checkAndRegisterTask(R resource) {
    var resourceID = ResourceID.fromResource(resource);
    if (timerTasks.get(resourceID) == null && (registerPredicate == null
        || registerPredicate.test(resource))) {
      var task =  new TimerTask() {
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
   *
   * @param resourceID of the target related resource
   * @return the cached value of the resource, if not present it gets the resource from the
   *         supplier. The value provided from the supplier is cached, but no new event is
   *         propagated.
   */
  public Optional<T> getValueFromCacheOrSupplier(ResourceID resourceID) {
    var cachedValue = getCachedValue(resourceID);
    if (cachedValue.isPresent()) {
      return cachedValue;
    } else {
      return getAndCacheResource(resourceID);
    }
  }

  public interface ResourceSupplier<T, R> {
    Optional<T> getResources(R resource);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }
}
