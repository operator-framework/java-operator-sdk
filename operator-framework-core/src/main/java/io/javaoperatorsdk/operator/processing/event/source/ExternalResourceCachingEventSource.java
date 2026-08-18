/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.RecentOperationCacheFiller;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.ResourceIDProvider;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Handles caching and related operation of external event sources. It can handle multiple secondary
 * resources for a single primary resources.
 *
 * <p>There are two related concepts to understand:
 *
 * <ul>
 *   <li>ResourceIDMapper - maps/extracts a key used to reference the associated resource in the
 *       cache
 *   <li>Object equals usage - compares if the two resources are the same or same version.
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
public abstract class ExternalResourceCachingEventSource<R, P extends HasMetadata, ID>
    extends AbstractEventSource<R, P> implements RecentOperationCacheFiller<R> {

  private static final Logger log =
      LoggerFactory.getLogger(ExternalResourceCachingEventSource.class);

  protected final ResourceIDMapper<R, ID> resourceIDMapper;

  protected Map<ResourceID, Map<ID, R>> cache = new ConcurrentHashMap<>();

  /**
   * The resources written by the reconciler ({@link #handleRecentResourceCreate(ResourceID,
   * Object)} and {@link #handleRecentResourceUpdate(ResourceID, Object, Object)}) that were not
   * seen yet in a subsequent update of the whole resource set of a primary. Such an update might
   * have been created (polled or received) before the resource was actually written, thus not
   * containing the new state yet. Since these updates are handled as the full actual state, the
   * write would be lost from the cache; the next reconciliation would then create a duplicate of an
   * already created resource, or repeat an already executed update. Note that a mark is dropped on
   * the first update, so a resource really deleted or changed in the meantime is not retained
   * indefinitely.
   *
   * @see #retainUnconfirmedWrites(ResourceID, Map)
   */
  private final Map<ResourceID, Map<ID, RecentWrite<R>>> unconfirmedWrites =
      new ConcurrentHashMap<>();

  /**
   * The last state of a resource written by the reconciler and every state it replaced since the
   * last update. There can be multiple replaced states if the reconciler wrote the resource more
   * than once without an update in between; an update created before any of those writes is stale.
   * The set is empty if the resource was created, since there was no previous state then.
   */
  private record RecentWrite<R>(R written, Set<R> replaced) {}

  protected ExternalResourceCachingEventSource(
      Class<R> resourceClass, ResourceIDMapper<R, ID> resourceIDMapper) {
    this(null, resourceClass, resourceIDMapper);
  }

  protected ExternalResourceCachingEventSource(
      String name, Class<R> resourceClass, ResourceIDMapper<R, ID> resourceIDMapper) {
    super(resourceClass, name);
    if (resourceIDMapper == ResourceIDMapper.resourceIdProviderMapper()
        && !ResourceIDProvider.class.isAssignableFrom(resourceClass)) {
      throw new IllegalArgumentException(
          resourceClass.getName()
              + " is not a subclass of "
              + ResourceIDProvider.class.getSimpleName());
    }
    this.resourceIDMapper = resourceIDMapper;
  }

  protected synchronized void handleDelete(ResourceID primaryID) {
    unconfirmedWrites.remove(primaryID);
    var res = cache.remove(primaryID);
    if (res != null && deleteAcceptedByFilter(res.values())) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  protected synchronized void handleDeletes(ResourceID primaryID, Set<R> resource) {
    handleDelete(
        primaryID, resource.stream().map(resourceIDMapper::idFor).collect(Collectors.toSet()));
  }

  protected synchronized void handleDelete(ResourceID primaryID, R resource) {
    handleDelete(primaryID, Set.of(resourceIDMapper.idFor(resource)));
  }

  protected synchronized void handleDelete(ResourceID primaryID, Set<ID> resourceIDs) {
    if (!isRunning()) {
      return;
    }
    var unconfirmed = unconfirmedWrites.get(primaryID);
    if (unconfirmed != null) {
      unconfirmed.keySet().removeAll(resourceIDs);
      if (unconfirmed.isEmpty()) {
        unconfirmedWrites.remove(primaryID);
      }
    }
    var cachedValues = cache.get(primaryID);
    List<R> removedResources =
        cachedValues == null
            ? Collections.emptyList()
            : resourceIDs.stream()
                .flatMap(id -> Stream.ofNullable(cachedValues.remove(id)))
                .collect(Collectors.toList());

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
    var toDelete = cache.keySet().stream().filter(k -> !allNewResources.containsKey(k)).toList();
    toDelete.forEach(
        primaryID -> {
          if (unconfirmedWrites.containsKey(primaryID)) {
            // handled as an empty update, so that a recently written resource, that this update
            // could not see yet, is not removed from the cache
            handleResources(primaryID, Collections.emptySet());
          } else {
            handleDelete(primaryID);
          }
        });
    allNewResources.forEach(this::handleResources);
  }

  protected synchronized void handleResources(
      ResourceID primaryID, Set<R> newResources, boolean propagateEvent) {
    log.debug(
        "Handling resources update for: {} numberOfResources: {} ", primaryID, newResources.size());
    if (!isRunning()) {
      return;
    }
    var cachedResources = cache.get(primaryID);
    if (cachedResources == null) {
      cachedResources = Collections.emptyMap();
    }
    var newResourcesMap =
        newResources.stream().collect(Collectors.toMap(resourceIDMapper::idFor, r -> r));
    retainUnconfirmedWrites(primaryID, newResourcesMap);
    cache.put(primaryID, newResourcesMap);
    if (propagateEvent
        && !newResourcesMap.equals(cachedResources)
        && acceptedByFiler(cachedResources, newResourcesMap)) {
      getEventHandler().handleEvent(new Event(primaryID));
    }
  }

  /**
   * Keeps the resources written since the received update was created, thus missing from it. An
   * update is considered stale for a written resource if it does not contain it at all - which is
   * the expected case for a create - or if it still contains a state that a write replaced. Any
   * other state is a change that happened outside of the reconciler, so it is accepted as the
   * actual state.
   *
   * @see #unconfirmedWrites
   */
  private void retainUnconfirmedWrites(ResourceID primaryID, Map<ID, R> newResourcesMap) {
    var unconfirmed = unconfirmedWrites.remove(primaryID);
    if (unconfirmed == null) {
      return;
    }
    unconfirmed.forEach(
        (id, write) -> {
          var newResource = newResourcesMap.get(id);
          if (newResource == null || write.replaced().contains(newResource)) {
            log.debug(
                "Retaining recently written resource missing from the update. Primary ID: {},"
                    + " resource ID: {}",
                primaryID,
                id);
            newResourcesMap.put(id, write.written());
          }
        });
  }

  private boolean acceptedByFiler(Map<ID, R> cachedResourceMap, Map<ID, R> newResourcesMap) {

    var addedResources = new HashMap<>(newResourcesMap);
    addedResources.keySet().removeAll(cachedResourceMap.keySet());
    if (onAddFilter != null || genericFilter != null) {
      var anyAddAccepted =
          addedResources.values().stream()
              .anyMatch(r -> acceptedByGenericFiler(r) && onAddFilter.accept(r));
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

    Map<ID, R> possibleUpdatedResources = new HashMap<>(cachedResourceMap);
    possibleUpdatedResources.keySet().retainAll(newResourcesMap.keySet());
    possibleUpdatedResources =
        possibleUpdatedResources.entrySet().stream()
            .filter(entry -> !newResourcesMap.get(entry.getKey()).equals(entry.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (onUpdateFilter != null || genericFilter != null) {
      return possibleUpdatedResources.entrySet().stream()
          .anyMatch(
              entry -> {
                var newResource = newResourcesMap.get(entry.getKey());
                return acceptedByGenericFiler(newResource)
                    && onUpdateFilter.accept(newResource, entry.getValue());
              });
    } else return !possibleUpdatedResources.isEmpty();
  }

  private boolean acceptedByGenericFiler(R resource) {
    return genericFilter == null || genericFilter.accept(resource);
  }

  @Override
  public synchronized void handleRecentResourceCreate(ResourceID primaryID, R resource) {
    var actualValues = cache.get(primaryID);
    var resourceId = resourceIDMapper.idFor(resource);
    if (actualValues == null) {
      actualValues = new HashMap<>();
      cache.put(primaryID, actualValues);
      actualValues.put(resourceId, resource);
    } else {
      actualValues.computeIfAbsent(resourceId, r -> resource);
    }
    markUnconfirmedWrite(primaryID, resourceId, resource, null);
  }

  @Override
  public synchronized void handleRecentResourceUpdate(
      ResourceID primaryID, R resource, R previousVersionOfResource) {
    var actualValues = cache.get(primaryID);
    if (actualValues != null) {
      var resourceId = resourceIDMapper.idFor(resource);
      R actualResource = actualValues.get(resourceId);
      if (actualResource != null && actualResource.equals(previousVersionOfResource)) {
        actualValues.put(resourceId, resource);
        markUnconfirmedWrite(primaryID, resourceId, resource, previousVersionOfResource);
      }
    }
  }

  /**
   * Marks the written resource as not confirmed yet by an update, keeping the states replaced by
   * previous writes of the same resource. Without those, an update created before an earlier write
   * would not be recognized as stale, and the last write would be lost from the cache.
   *
   * @param replaced the state the write replaced, {@code null} if the resource was created
   * @see #unconfirmedWrites
   */
  private void markUnconfirmedWrite(ResourceID primaryID, ID resourceId, R written, R replaced) {
    unconfirmedWrites
        .computeIfAbsent(primaryID, id -> new HashMap<>())
        .compute(
            resourceId,
            (id, previousWrite) -> {
              Set<R> replacedStates =
                  previousWrite == null ? new HashSet<>() : new HashSet<>(previousWrite.replaced());
              if (replaced != null) {
                replacedStates.add(replaced);
              }
              return new RecentWrite<>(written, replacedStates);
            });
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
      return new HashSet<>(cachedValues.values());
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

  public Map<ResourceID, Map<ID, R>> getCache() {
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
