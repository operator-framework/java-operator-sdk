package io.javaoperatorsdk.operator.processing.event.source;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Handles caching and related operation of external event sources. It can handle multiple secondary
 * resources for a single primary resources.
 * <p>
 * There are two related concepts to understand:
 * <ul>
 * <li>CacheKeyMapper - maps/extracts a key used to reference the associated resource in the
 * cache</li>
 * <li>Object equals usage - compares if the two resources are the same or same version.</li>
 * </ul>
 *
 * When a resource is added for a primary resource its key is used to put in a map. Equals is used
 * to compare if it's still the same resource, or an updated version of it. Event is emitted only if
 * a new resource(s) is received or actually updated or deleted. Delete is detected by a missing
 * key.
 *
 * @param <R> type of polled external secondary resource
 * @param <P> primary resource
 */
public abstract class ExternalResourceCachingEventSource<R, P extends HasMetadata>
    extends AbstractResourceEventSource<R, P> implements RecentOperationCacheFiller<R> {

  private static Logger log = LoggerFactory.getLogger(ExternalResourceCachingEventSource.class);

  protected final CacheKeyMapper<R> cacheKeyMapper;

  protected Map<ResourceID, Map<String, R>> cache = new ConcurrentHashMap<>();

  protected ExternalResourceCachingEventSource(Class<R> resourceClass,
      CacheKeyMapper<R> cacheKeyMapper) {
    super(resourceClass);
    this.cacheKeyMapper = cacheKeyMapper;
  }

  protected synchronized void handleDelete(ResourceID primaryID) {
    var res = cache.remove(primaryID);
    if (res != null && deleteAcceptedByFilter(res.values())) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  protected synchronized void handleDeletes(ResourceID primaryID, Set<R> resource) {
    handleDelete(primaryID,
        resource.stream().map(cacheKeyMapper::keyFor).collect(Collectors.toSet()));
  }

  protected synchronized void handleDelete(ResourceID primaryID, R resource) {
    handleDelete(primaryID, Set.of(cacheKeyMapper.keyFor(resource)));
  }

  protected synchronized void handleDelete(ResourceID primaryID, Set<String> resourceIDs) {
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
    if (!removedResources.isEmpty() && deleteAcceptedByFilter(removedResources)) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  protected synchronized void handleResources(ResourceID primaryID, R actualResource) {
    handleResources(primaryID, Set.of(actualResource), true);
  }

  protected synchronized void handleResources(ResourceID primaryID, Set<R> newResources) {
    handleResources(primaryID, newResources, true);
  }

  protected synchronized void handleResources(Map<ResourceID, Set<R>> allNewResources) {
    var toDelete = cache.keySet().stream().filter(k -> !allNewResources.containsKey(k))
        .collect(Collectors.toList());
    toDelete.forEach(this::handleDelete);
    allNewResources.forEach(this::handleResources);
  }

  protected synchronized void handleResources(ResourceID primaryID, Set<R> newResources,
      boolean propagateEvent) {
    log.debug("Handling resources update for: {} numberOfResources: {} ", primaryID,
        newResources.size());
    if (!isRunning()) {
      return;
    }
    var cachedResources = cache.get(primaryID);
    if (cachedResources == null) {
      cachedResources = Collections.emptyMap();
    }
    var newResourcesMap =
        newResources.stream().collect(Collectors.toMap(cacheKeyMapper::keyFor, r -> r));
    cache.put(primaryID, newResourcesMap);
    if (propagateEvent && !newResourcesMap.equals(cachedResources)
        && acceptedByFiler(cachedResources, newResourcesMap)) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  private boolean acceptedByFiler(Map<String, R> cachedResourceMap,
      Map<String, R> newResourcesMap) {

    var addedResources = new HashMap<>(newResourcesMap);
    addedResources.keySet().removeAll(cachedResourceMap.keySet());
    if (onAddFilter != null || genericFilter != null) {
      var anyAddAccepted =
          addedResources.values().stream().anyMatch(r -> acceptedByGenericFiler(r) &&
              onAddFilter.accept(r));
      if (anyAddAccepted) {
        return true;
      }
    } else if (!addedResources.isEmpty()) {
      return true;
    }

    var deletedResource = new HashMap<>(cachedResourceMap);
    deletedResource.keySet().removeAll(newResourcesMap.keySet());
    if (onDeleteFilter != null || genericFilter != null) {
      var anyDeleteAccepted =
          deletedResource.values().stream()
              .anyMatch(r -> acceptedByGenericFiler(r) && onDeleteFilter.accept(r, false));
      if (anyDeleteAccepted) {
        return true;
      }
    } else if (!deletedResource.isEmpty()) {
      return true;
    }

    Map<String, R> possibleUpdatedResources = new HashMap<>(cachedResourceMap);
    possibleUpdatedResources.keySet().retainAll(newResourcesMap.keySet());
    possibleUpdatedResources = possibleUpdatedResources.entrySet().stream()
        .filter(entry -> !newResourcesMap
            .get(entry.getKey()).equals(entry.getValue()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (onUpdateFilter != null || genericFilter != null) {
      var anyUpdated = possibleUpdatedResources.entrySet().stream()
          .anyMatch(
              entry -> {
                var newResource = newResourcesMap.get(entry.getKey());
                return acceptedByGenericFiler(newResource) &&
                    onUpdateFilter.accept(newResource, entry.getValue());
              });
      if (anyUpdated) {
        return true;
      }
    } else if (!possibleUpdatedResources.isEmpty()) {
      return true;
    }

    return false;
  }

  private boolean acceptedByGenericFiler(R resource) {
    return genericFilter == null || genericFilter.accept(resource);
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID primaryID, R resource) {
    var actualValues = cache.get(primaryID);
    var resourceId = cacheKeyMapper.keyFor(resource);
    if (actualValues == null) {
      actualValues = new HashMap<>();
      cache.put(primaryID, actualValues);
      actualValues.put(resourceId, resource);
    } else {
      actualValues.computeIfAbsent(resourceId, r -> resource);
    }
  }

  @Override
  public synchronized void handleRecentResourceUpdate(
      ResourceID primaryID, R resource, R previousVersionOfResource) {
    var actualValues = cache.get(primaryID);
    if (actualValues != null) {
      var resourceId = cacheKeyMapper.keyFor(resource);
      R actualResource = actualValues.get(resourceId);
      if (actualResource.equals(previousVersionOfResource)) {
        actualValues.put(resourceId, resource);
      }
    }
  }

  @Override
  public Set<R> getSecondaryResources(P primary) {
    return getSecondaryResources(ResourceID.fromResource(primary));
  }

  public Set<R> getSecondaryResources(ResourceID primaryID) {
    var cachedValues = cache.get(primaryID);
    if (cachedValues == null) {
      return Collections.emptySet();
    } else {
      return new HashSet<>(cache.get(primaryID).values());
    }
  }

  public Optional<R> getSecondaryResource(ResourceID primaryID) {
    var resources = getSecondaryResources(primaryID);
    if (resources.isEmpty()) {
      return Optional.empty();
    } else if (resources.size() == 1) {
      return Optional.of(resources.iterator().next());
    } else {
      throw new IllegalStateException("More than 1 secondary resource related to primary");
    }
  }

  public Map<ResourceID, Map<String, R>> getCache() {
    return Collections.unmodifiableMap(cache);
  }

  protected boolean deleteAcceptedByFilter(Collection<R> res) {
    if (onDeleteFilter == null) {
      return true;
    }
    // it is enough if at least one event is accepted
    // Cannot be sure about the final state in general, mainly for polled resources. This might be
    // fine-tuned for
    // other event sources. (For now just by overriding this method.)
    return res.stream().anyMatch(r -> onDeleteFilter.accept(r, false));
  }
}
