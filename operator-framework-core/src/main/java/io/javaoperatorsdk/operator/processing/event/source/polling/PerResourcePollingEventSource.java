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
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventFilter;
import io.javaoperatorsdk.operator.processing.event.source.LifecycleAwareEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public class PerResourcePollingEventSource<T, R extends HasMetadata>
    extends LifecycleAwareEventSource
    implements ResourceEventAware<R> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  private final Timer timer = new Timer();
  // todo real cache
  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final Map<ResourceID, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceSupplier<T, R> resourceSupplier;
  private final ResourceCache<R> resourceCache;
  private final EventFilter<T> eventFilter;
  private final Predicate<R> registerPredicate;
  private final long period;

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period, EventFilter<T> eventFilter) {
    this(resourceSupplier, resourceCache, period, eventFilter, null);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period, Predicate<R> registerPredicate) {
    this(resourceSupplier, resourceCache, period, null, registerPredicate);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period) {
    this(resourceSupplier, resourceCache, period, null, null);
  }

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache, long period,
      EventFilter<T> eventFilter,
      Predicate<R> registerPredicate) {
    this.resourceSupplier = resourceSupplier;
    this.resourceCache = resourceCache;
    this.period = period;
    this.eventFilter = eventFilter;
    this.registerPredicate = registerPredicate;
  }

  private void pollForResource(R resource) {
    var value = resourceSupplier.getResources(resource);
    var resourceID = ResourceID.fromResource(resource);
    var cachedValue = cache.get(resourceID);
    if (value.isEmpty() && cachedValue != null) {
      cache.remove(resourceID);
      eventHandler.handleEvent(new Event(resourceID));
    } else {
      value.ifPresent(v -> {
        if (cachedValue == null || !cachedValue.equals(v)) {
          cache.put(resourceID, v);
          if (eventFilter == null || eventFilter.accept(v, cachedValue, resourceID)) {
            eventHandler.handleEvent(new Event(resourceID));
          }
        }
      });
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
    if (timerTasks.get(resourceID) == null || registerPredicate == null
        || registerPredicate.test(resource)) {
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          if (!isStarted()) {
            log.debug("Event source not start yet. Will not run for: {}", resourceID);
            return;
          }
          var res = resourceCache.get(resourceID);
          res.ifPresentOrElse(r -> pollForResource(r),
              () -> log.warn("No resource in cache for resource ID: {}", resourceID));
        }
      }, period, period);
    }
  }

  public Optional<T> getCachedResource(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
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
