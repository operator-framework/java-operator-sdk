package io.javaoperatorsdk.operator.processing.event.source.inbound;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

public class CachingInboundEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P>
    implements ResourceEventAware<P> {

  private final Timer timer;
  private final Map<String, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceFetcher<R, P> resourceFetcher;
  private final long period;
  private final Set<ResourceID> fetchedForPrimaries = ConcurrentHashMap.newKeySet();

  public CachingInboundEventSource(
      ResourceFetcher<R, P> resourceFetcher, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    this(resourceFetcher, resourceClass, cacheKeyMapper, 0);
  }

  public CachingInboundEventSource(
      ResourceFetcher<R, P> resourceFetcher, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper, long period) {
    super(resourceClass, cacheKeyMapper);
    this.resourceFetcher = resourceFetcher;
    this.period = period;
    this.timer = period > 0 ? new Timer() : null;
  }

  public void handleResourceEvent(ResourceID primaryID, Set<R> resources) {
    super.handleResources(primaryID, resources);

    resources.forEach(resource -> checkAndRegisterTask(primaryID, resource));
  }

  public void handleResourceEvent(ResourceID primaryID, R resource) {
    super.handleResources(primaryID, resource);

    checkAndRegisterTask(primaryID, resource);
  }

  public void handleResourceDeleteEvent(ResourceID primaryID, String resourceID) {
    super.handleDelete(primaryID, Set.of(resourceID));

    TimerTask task = timerTasks.remove(resourceID);
    if (task != null) {
      task.cancel();
    }
  }

  @Override
  public void onResourceDeleted(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    fetchedForPrimaries.remove(resourceID);
  }

  private Set<R> getAndCacheResource(P primary) {
    var primaryID = ResourceID.fromResource(primary);
    var values = resourceFetcher.fetchResources(primary);
    handleResources(primaryID, values, false);
    fetchedForPrimaries.add(primaryID);
    values.forEach(resource -> checkAndRegisterTask(primaryID, resource));
    return values;
  }

  private void invalidCacheResource(ResourceID primaryID, String secondaryID) {
    handleDelete(primaryID, Set.of(secondaryID), false);
  }

  private void checkAndRegisterTask(ResourceID primaryID, R resource) {
    var secondaryID = cacheKeyMapper.keyFor(resource);
    timerTasks.computeIfAbsent(secondaryID, key -> {
      var task = new TimerTask() {
        @Override
        public void run() {
          invalidCacheResource(primaryID, key);
        }
      };
      timer.schedule(task, period, period);
      return task;
    });
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
        return getAndCacheResource(primary);
      }
    }
  }

  public interface ResourceFetcher<R, P> {
    Set<R> fetchResources(P primaryResource);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    if (timer != null) {
      timer.cancel();
    }
  }

}
