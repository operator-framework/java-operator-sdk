package io.javaoperatorsdk.operator.processing.event.source;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public abstract class ExternalResourceCachingEventSource<R, P extends HasMetadata>
    extends AbstractResourceEventSource<R, P> implements RecentOperationCacheFiller<R> {

  private static Logger log = LoggerFactory.getLogger(ExternalResourceCachingEventSource.class);

  protected final IDMapper<R> idMapper;

  protected Map<ResourceID, Map<String, R>> cache = new ConcurrentHashMap<>();

  protected ExternalResourceCachingEventSource(Class<R> resourceClass, IDMapper<R> idMapper) {
    super(resourceClass);
    this.idMapper = idMapper;
  }

  protected synchronized void handleDelete(ResourceID primaryID) {
    var res = cache.remove(primaryID);
    if (res != null) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  protected synchronized void handleDelete(ResourceID primaryID, String resourceID) {
    handleDelete(primaryID, Set.of(resourceID));
  }

  protected synchronized void handleDeleteResources(ResourceID primaryID, Set<R> resource) {
    handleDelete(primaryID, resource.stream().map(idMapper).collect(Collectors.toSet()));
  }

  protected synchronized void handleDelete(ResourceID primaryID, R resource) {
    handleDelete(primaryID, Set.of(idMapper.apply(resource)));
  }

  protected synchronized void handleDelete(ResourceID primaryID, Set<String> resourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValues = cache.get(primaryID);
    var sizeBeforeRemove = cachedValues.size();
    resourceID.forEach(cachedValues::remove);

    if (cachedValues.isEmpty()) {
      cache.remove(primaryID);
    }
    if (sizeBeforeRemove > cachedValues.size()) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  protected synchronized void handleResourcesUpdate(ResourceID primaryID, R actualResource) {
    handleResourcesUpdate(primaryID, Set.of(actualResource), true);
  }

  protected synchronized void handleResourcesUpdate(ResourceID primaryID, Set<R> newResources) {
    handleResourcesUpdate(primaryID, newResources, true);
  }

  protected synchronized void handleResourcesUpdate(Map<ResourceID, Set<R>> allNewResources) {
    var toDelete = cache.keySet().stream().filter(k -> !allNewResources.containsKey(k))
        .collect(Collectors.toList());
    toDelete.forEach(this::handleDelete);
    allNewResources.forEach((primaryID, resources) -> handleResourcesUpdate(primaryID, resources));
  }

  protected synchronized void handleResourcesUpdate(ResourceID primaryID, Set<R> newResources,
      boolean propagateEvent) {
    log.debug("Handling resources update for: {} numberOfResources: {} ", primaryID,
        newResources.size());
    if (!isRunning()) {
      return;
    }
    var cachedResources = cache.get(primaryID);
    var newResourcesMap = newResources.stream().collect(Collectors.toMap(idMapper, r -> r));
    cache.put(primaryID, newResourcesMap);
    if (propagateEvent && !newResourcesMap.equals(cachedResources)) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID primaryID, R resource) {
    var actualValues = cache.get(primaryID);
    var resourceId = idMapper.apply(resource);
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
      var resourceId = idMapper.apply(resource);
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
}
