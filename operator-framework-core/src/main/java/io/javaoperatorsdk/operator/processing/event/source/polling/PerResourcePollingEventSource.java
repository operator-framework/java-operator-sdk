package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.cache.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingFilteringEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventFilter;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public class PerResourcePollingEventSource<T, R extends HasMetadata>
    extends CachingFilteringEventSource<T>
    implements ResourceEventAware<R> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  private final Timer timer = new Timer();
  private final Map<ResourceID, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceSupplier<T, R> resourceSupplier;
  private final ResourceCache<R> resourceCache;
  private final Predicate<R> registerPredicate;
  private final long period;

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period, Cache<ResourceID, T> cache,
      EventFilter<T> eventFilter) {
    this(resourceSupplier, resourceCache, period, cache, eventFilter, null);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period, Cache<ResourceID, T> cache,
      Predicate<R> registerPredicate) {
    this(resourceSupplier, resourceCache, period, cache, null, registerPredicate);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period, Cache<ResourceID, T> cache) {
    this(resourceSupplier, resourceCache, period, cache, null, null);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period, Cache<ResourceID, T> cache,
      EventFilter<T> eventFilter, Predicate<R> registerPredicate) {
    super(cache, eventFilter);
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
      task.cancel();
    }
    cache.remove(resourceID);
  }

  private void checkAndRegisterTask(R resource) {
    var resourceID = ResourceID.fromResource(resource);
    if (timerTasks.get(resourceID) == null && (registerPredicate == null
        || registerPredicate.test(resource))) {
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          if (!isRunning()) {
            log.debug("Event source not start yet. Will not run for: {}", resourceID);
            return;
          }
          // always use up-to-date resource from cache
          var res = resourceCache.get(resourceID);
          res.ifPresentOrElse(r -> pollForResource(r),
              () -> log.warn("No resource in cache for resource ID: {}", resourceID));
        }
      }, period, period);
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
