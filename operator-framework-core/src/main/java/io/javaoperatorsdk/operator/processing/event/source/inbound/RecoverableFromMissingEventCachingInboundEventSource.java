package io.javaoperatorsdk.operator.processing.event.source.inbound;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;

/**
 *
 * Invalidate the cache for each known resource after a given period of time. If a known resource is
 * requested and a new event do not update the cache, the resource is fetched. An event will be
 * triggered when an event is received after the cache has been invalidated For other behavior see
 * {@link ExternalResourceCachingEventSource}
 *
 * @param <R> the resource polled by the event source
 * @param <P> related custom resource
 */
public class RecoverableFromMissingEventCachingInboundEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P>
    implements ResourceEventAware<P> {

  private Timer timer = new Timer();
  private final Map<String, TimerTask> timerTasks = new ConcurrentHashMap<>();
  private final ResourceFetcher<R, P> resourceFetcher;
  private final long period;
  private final Set<ResourceID> fetchedForPrimaries = ConcurrentHashMap.newKeySet();
  protected Map<ResourceID, Set<String>> knownSecondaryResources = new ConcurrentHashMap<>();

  public RecoverableFromMissingEventCachingInboundEventSource(
      ResourceFetcher<R, P> resourceFetcher, Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper, long period) {
    super(resourceClass, cacheKeyMapper);
    this.resourceFetcher = resourceFetcher;
    this.period = period;
  }

  public void handleResourceEvent(ResourceID primaryID, Set<R> resources) {
    // an event will be triggered for known expired entries (cache will see them as
    // new resource)
    super.handleResources(primaryID, resources);

    resources.forEach(resource -> checkAndRegisterTask(primaryID, resource));
  }

  public void handleResourceEvent(ResourceID primaryID, R resource) {
    // an event will be triggered if the cache expired
    super.handleResources(primaryID, resource);

    checkAndRegisterTask(primaryID, resource);
  }

  public void handleResourceDeleteEvent(ResourceID primaryID, String resourceID) {
    super.handleDelete(primaryID, Set.of(resourceID));

    removeKnownResource(primaryID, resourceID);
  }

  /**
   * Overriden to avoid triggering event twice when the resource is in cache and not stale
   */
  @Override
  protected synchronized void handleDelete(ResourceID primaryID, Set<String> resourceIDs,
      boolean propagateEvent) {
    if (!isRunning()) {
      return;
    }
    var cachedValues = cache.get(primaryID);
    List<R> removedResources = cachedValues == null ? Collections.emptyList()
        : resourceIDs.stream()
            .flatMap(id -> Stream.ofNullable(cachedValues.remove(id))).collect(Collectors.toList());

    if (cachedValues != null && cachedValues.isEmpty()) {
      cache.remove(primaryID);
    }
    if (propagateEvent && !removedResources.isEmpty() && deleteAcceptedByFilter(removedResources)) {
      getEventHandler().handleEvent(new Event(primaryID));
    } else if (resourceIDs.stream()
        .anyMatch(deletedId -> knownSecondaryResources.get(primaryID).contains(deletedId))) {
      // event cannot be filtered by onDeleteFilter
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  @Override
  public void onResourceDeleted(P resource) {
    var resourceID = ResourceID.fromResource(resource);
    fetchedForPrimaries.remove(resourceID);
    knownSecondaryResources.remove(resourceID);
  }

  private Set<R> getAndCacheResources(P primary) {
    var primaryID = ResourceID.fromResource(primary);
    var values = resourceFetcher.fetchResources(primary);
    handleResources(primaryID, values, false);
    fetchedForPrimaries.add(primaryID);
    values.forEach(resource -> checkAndRegisterTask(primaryID, resource));
    return values;
  }

  private R getAndCacheResource(ResourceID primaryID, String secondaryID) {
    var value = resourceFetcher.fetchResource(secondaryID);
    if (value != null) {
      // missed a create/update
      handleResources(primaryID, Set.of(value), false);
    } else {
      // missed a delete event
      removeKnownResource(primaryID, secondaryID);
    }

    return value;
  }

  private void invalidCacheResource(ResourceID primaryID, String secondaryID) {
    // will let the cache unaware of this secondaryID
    // whener the cache is fetched, it must be reconciled with known secondary
    // resources
    handleDelete(primaryID, Set.of(secondaryID), false);
  }

  private void checkAndRegisterTask(ResourceID primaryID, R resource) {
    var secondaryID = cacheKeyMapper.keyFor(resource);
    knownSecondaryResources.get(primaryID).add(secondaryID);
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

  private void removeKnownResource(ResourceID primaryID, String resourceID) {
    TimerTask task = timerTasks.remove(resourceID);
    if (task != null) {
      task.cancel();
    }
    knownSecondaryResources.get(primaryID).remove(resourceID);
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
      return addMissingKnownSecondaryResources(primaryID, cachedValue.values());
    } else {
      if (fetchedForPrimaries.contains(primaryID)) {
        return addMissingKnownSecondaryResources(primaryID, Collections.emptySet());
      } else {
        return getAndCacheResources(primary);
      }
    }
  }

  private Set<R> addMissingKnownSecondaryResources(ResourceID primaryID,
      Collection<R> cachedResources) {
    Set<String> cachedIDs =
        cachedResources.stream().map(cacheKeyMapper::keyFor).collect(Collectors.toSet());
    knownSecondaryResources.get(primaryID).forEach(secondaryID -> {
      if (!cachedIDs.contains(secondaryID)) {
        R secondary = getAndCacheResource(primaryID, secondaryID);
        if (secondary != null) {
          cachedResources.add(secondary);
        }
      }
    });

    return new HashSet<R>(cachedResources);
  }

  public interface ResourceFetcher<R, P> {
    Set<R> fetchResources(P primaryResource);

    R fetchResource(String secondaryID);
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    timer.cancel();
  }
}
