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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

/**
 * Temporal cache is used to solve the problem for {@link KubernetesDependentResource} that is, when
 * a create or update is executed the subsequent getResource operation might not return the
 * up-to-date resource from informer cache, since it is not received yet.
 *
 * <p>Since an update (for create is simpler) was done successfully we can temporarily track that
 * resource if its version is later than the events we've processed. We then know that we can skip
 * all events that have the same resource version or earlier than the tracked resource. Once we
 * process an event that has the same resource version or later, then we know the tracked resource
 * can be removed.
 *
 * <p>In some cases it is possible for the informer to deliver events prior to the attempt to put
 * the resource in the temporal cache. The startModifying/doneModifying methods are used to pause
 * event delivery to ensure that temporal cache recognizes the put entry as an event that can be
 * skipped.
 *
 * <p>If comparable resource versions are disabled, then this cache is effectively disabled.
 *
 * <p>Some principles to realize with the current filtering algorithm:
 *
 * <ul>
 *   <li>We propagate events only if we received an event that has the same resourceVersion or newer
 *       than resource version from update
 *   <li>The propagated event should correspond to a possible real world scenario - considering also
 *       ones that could happen if the Informer does a re-list.
 * </ul>
 *
 * @param <T> resource to cache.
 */
public class TemporaryResourceCache<T extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(TemporaryResourceCache.class);

  private final boolean comparableResourceVersions;
  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final EventFilterSupport eventFilteringSupport = new EventFilterSupport();

  private final ManagedInformerEventSource<T, ?, ?> managedInformerEventSource;

  public TemporaryResourceCache(
      boolean comparableResourceVersions,
      ManagedInformerEventSource<T, ?, ?> managedInformerEventSource) {
    this.comparableResourceVersions = comparableResourceVersions;
    this.managedInformerEventSource = managedInformerEventSource;
  }

  public synchronized void startEventFilteringModify(ResourceID resourceID) {
    if (!comparableResourceVersions) {
      return;
    }
    eventFilteringSupport.startEventFilteringModify(resourceID);
  }

  public synchronized Optional<GenericResourceEvent> doneEventFilterModify(ResourceID resourceID) {
    if (!comparableResourceVersions) {
      return Optional.empty();
    }
    return eventFilteringSupport.doneEventFilterModify(resourceID);
  }

  public Optional<GenericResourceEvent> onDeleteEvent(T resource, boolean unknownState) {
    return onEvent(ResourceAction.DELETED, resource, null, unknownState);
  }

  public Optional<GenericResourceEvent> onAddOrUpdateEvent(
      ResourceAction action, T resource, T prevResourceVersion) {
    return onEvent(action, resource, prevResourceVersion, null);
  }

  private synchronized Optional<GenericResourceEvent> onEvent(
      ResourceAction action, T resource, T prevResourceVersion, Boolean unknownState) {
    GenericResourceEvent actualEvent =
        toGenericResourceEvent(action, resource, prevResourceVersion, unknownState);
    if (!comparableResourceVersions) {
      return Optional.of(actualEvent);
    }
    var resourceId = ResourceID.fromResource(resource);
    log.debug(
        "Processing event in temp cache. id={}, action={}, rv={}, unknownState={}",
        resourceId,
        action,
        resource.getMetadata().getResourceVersion(),
        unknownState);
    var cached = cache.get(resourceId);
    if (cached != null) {
      int comp = ReconcilerUtilsInternal.compareResourceVersions(resource, cached);
      if (comp >= 0 || Boolean.TRUE.equals(unknownState)) {
        log.debug(
            "Removing resource from temp cache. id={}, comparison={}, unknownState={}",
            resourceId,
            comp,
            unknownState);
        cache.remove(resourceId);
      } else {
        log.debug(
            "Keeping temp cache entry; event rv {} is older than cached rv {}. id={}",
            resource.getMetadata().getResourceVersion(),
            cached.getMetadata().getResourceVersion(),
            resourceId);
      }
    }
    return eventFilteringSupport.processEvent(resourceId, actualEvent);
  }

  static <T extends HasMetadata> GenericResourceEvent toGenericResourceEvent(
      ResourceAction action, T resource, T prevResourceVersion, Boolean unknownState) {
    return new GenericResourceEvent(action, resource, prevResourceVersion, unknownState);
  }

  /** put the item into the cache if it's for a later state than what has already been observed. */
  public synchronized void putResource(T newResource) {
    if (!comparableResourceVersions) {
      return;
    }

    var resourceId = ResourceID.fromResource(newResource);

    if (newResource.getMetadata().getResourceVersion() == null) {
      log.warn(
          "Resource {}: with no resourceVersion put in temporary cache. This is not the expected"
              + " usage pattern, only resources returned from the api server should be put in the"
              + " cache.",
          resourceId);
      return;
    }

    var ns = newResource.getMetadata().getNamespace();
    // this can happen when we dynamically change the followed namespace list
    if (!managedInformerEventSource.manager().isWatchingNamespace(ns)) {
      log.debug(
          "Skipping caching of resource: {} since namespace is not being watched: {}",
          resourceId,
          ns);
      return;
    }

    var cachedResource = getResourceFromCache(resourceId).orElse(null);
    eventFilteringSupport.addToOwnResourceVersions(
        resourceId, newResource.getMetadata().getResourceVersion());

    // check against the latestResourceVersion processed by the TemporaryResourceCache
    // If the resource is older, then we can safely ignore.
    //
    // this also prevents resurrecting recently deleted entities for which the delete event
    // has already been processed
    var latestRV = getLastSyncResourceVersion(ns);
    if (latestRV != null
        && ReconcilerUtilsInternal.compareResourceVersions(
                latestRV, newResource.getMetadata().getResourceVersion())
            > 0) {
      log.debug(
          "Resource {}: resourceVersion {} is not later than latest {}",
          resourceId,
          newResource.getMetadata().getResourceVersion(),
          latestRV);
      return;
    }

    if (cachedResource == null
        || ReconcilerUtilsInternal.compareResourceVersions(newResource, cachedResource) > 0) {
      log.debug(
          "Temporarily moving ahead to target version {} for resource id: {}",
          newResource.getMetadata().getResourceVersion(),
          resourceId);
      cache.put(resourceId, newResource);
    } else {
      log.debug(
          "Skipping temp cache put; new rv {} is not later than cached rv {}. id={}",
          newResource.getMetadata().getResourceVersion(),
          cachedResource.getMetadata().getResourceVersion(),
          resourceId);
    }
  }

  private String getLastSyncResourceVersion(String namespace) {
    return managedInformerEventSource.manager().lastSyncResourceVersion(namespace);
  }

  /**
   * There are (probably extremely rare) circumstances, when we can miss a delete event related to a
   * resources: when we create a resource that is deleted right after by third party and the related
   * informer have a disconnected watch and this watch needs to do a re-list when connected again.
   * In this case neither the ADD nor DELETE event will be propagated to the informer, but we
   * explicitly add resources to this cache. Those are cleaned up by this check, which is triggered
   * by the informer's onList callback.
   */
  public synchronized void checkGhostResources() {
    log.debug("Checking for ghost resources.");
    var iterator = cache.entrySet().iterator();
    while (iterator.hasNext()) {
      var e = iterator.next();

      var ns = e.getValue().getMetadata().getNamespace();
      // this can happen if followed namespaces are changed dynamically
      if (!managedInformerEventSource.manager().isWatchingNamespace(ns)) {
        log.debug(
            "Removing resource: {} from cache as part of ghost cleanup. Namespace is not followed"
                + " anymore: {}",
            e.getKey(),
            ns);
        iterator.remove();
        eventFilteringSupport.handleGhostResourceRemoval(e.getKey());
        continue;
      }
      if ((ReconcilerUtilsInternal.compareResourceVersions(
                  e.getValue().getMetadata().getResourceVersion(), getLastSyncResourceVersion(ns))
              < 0)
          // making sure we have the situation where resource is missing from the cache
          && managedInformerEventSource.manager().get(e.getKey()).isEmpty()) {
        log.debug("Removing ghost resource with ID: {}", e.getKey());
        iterator.remove();
        eventFilteringSupport.handleGhostResourceRemoval(e.getKey());
        managedInformerEventSource.handleEvent(ResourceAction.DELETED, e.getValue(), null, true);
      }
    }
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  synchronized boolean isEmpty() {
    return cache.isEmpty();
  }

  synchronized Map<ResourceID, T> getResources() {
    return Map.copyOf(cache);
  }

  EventFilterSupport getEventFilterSupport() {
    return eventFilteringSupport;
  }

  public void setOngoingRelist(String lastKnownSyncVersion) {
    eventFilteringSupport.setStartingReList();
  }

  public void setRelistFinished(String syncResourceVersions) {
    eventFilteringSupport.setRelistFinished();
  }
}
