package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// todo register predicate?
// todo event filter?

public class PerResourcePollingEventSource<T, R extends HasMetadata> extends AbstractEventSource
    implements ResourceEventAware<R> {

  private static final Logger log = LoggerFactory.getLogger(PerResourcePollingEventSource.class);

  private final Timer timer = new Timer();
  // todo real cache
  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final Map<ResourceID, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceSupplier<T, R> resourceSupplier;
  private final ResourceCache<R> resourceCache;
  private final long period;

  public PerResourcePollingEventSource(ResourceSupplier<T, R> resourceSupplier,
      ResourceCache<R> resourceCache,
      long period) {
    this.resourceSupplier = resourceSupplier;
    this.resourceCache = resourceCache;
    this.period = period;
  }

  private void pollForResource(R resource) {
    var resourceMap = resourceSupplier.getResources(resource);
    resourceMap.forEach((k, v) -> {
      var cachedObject = cache.get(k);
      if (cachedObject == null || !cachedObject.equals(v)) {
        cache.put(k, v);
        eventHandler.handleEvent(new Event(k));
      }
    });
  }

  @Override
  public void onResourceCreated(R resource) {
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        var resourceID = ResourceID.fromResource(resource);
        var res = resourceCache.get(resourceID);
        res.ifPresentOrElse( r -> pollForResource(r),
                ()-> log.warn("No resource in cache for resource ID: {}",resourceID) );

      }
    }, period, period);
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

  public Optional<T> getCachedResource(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  public interface ResourceSupplier<T, R> {
    Map<ResourceID, T> getResources(R resource);
  }
}
