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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceDeleteEvent;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

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
 * @param <T> resource to cache.
 */
public class TemporaryResourceCache<T extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(TemporaryResourceCache.class);

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final Map<ResourceID, EventFilterDetails> activeUpdates = new HashMap<>();
  private final boolean comparableResourceVersions;

  private final ManagedInformerEventSource<T, ?, ?> managedInformerEventSource;

  public enum EventHandling {
    DEFER,
    OBSOLETE,
    NEW
  }

  public TemporaryResourceCache(
      boolean comparableResourceVersions,
      long ghostResourceCheckInterval,
      ScheduledExecutorService ghostCheckExecutor,
      ManagedInformerEventSource<T, ?, ?> managedInformerEventSource) {
    this.comparableResourceVersions = comparableResourceVersions;
    this.managedInformerEventSource = managedInformerEventSource;
    if (comparableResourceVersions) {
      ghostCheckExecutor.scheduleWithFixedDelay(
          this::checkGhostResources,
          ghostResourceCheckInterval,
          ghostResourceCheckInterval,
          TimeUnit.MILLISECONDS);
    }
  }

  public synchronized void startEventFilteringModify(ResourceID resourceID) {
    if (!comparableResourceVersions) {
      return;
    }
    var ed = activeUpdates.computeIfAbsent(resourceID, id -> new EventFilterDetails());
    ed.increaseActiveUpdates();
  }

  public synchronized Optional<ResourceEvent> doneEventFilterModify(
      ResourceID resourceID, String updatedResourceVersion) {
    if (!comparableResourceVersions) {
      return Optional.empty();
    }
    var ed = activeUpdates.get(resourceID);
    if (ed == null || !ed.decreaseActiveUpdates(updatedResourceVersion)) {
      log.debug(
          "Active updates {} for resource id: {}",
          ed != null ? ed.getActiveUpdates() : 0,
          resourceID);
      return Optional.empty();
    }
    activeUpdates.remove(resourceID);
    var res = ed.getLatestEventAfterLastUpdateEvent();
    log.debug(
        "Zero active updates for resource id: {}; event after update event: {}; updated resource"
            + " version: {}",
        resourceID,
        res.isPresent(),
        updatedResourceVersion);
    return res;
  }

  public void onDeleteEvent(T resource, boolean unknownState) {
    onEvent(ResourceAction.DELETED, resource, null, unknownState, true);
  }

  public EventHandling onAddOrUpdateEvent(
      ResourceAction action, T resource, T prevResourceVersion) {
    return onEvent(action, resource, prevResourceVersion, false, false);
  }

  private synchronized EventHandling onEvent(
      ResourceAction action,
      T resource,
      T prevResourceVersion,
      boolean unknownState,
      boolean delete) {
    if (!comparableResourceVersions) {
      return EventHandling.NEW;
    }

    var resourceId = ResourceID.fromResource(resource);
    if (log.isDebugEnabled()) {
      log.debug("Processing event");
    }
    var cached = cache.get(resourceId);
    EventHandling result = EventHandling.NEW;
    if (cached != null) {
      int comp = ReconcilerUtilsInternal.compareResourceVersions(resource, cached);
      if (comp >= 0 || unknownState) {
        log.debug(
            "Removing resource from temp cache. comparison: {} unknown state: {}",
            comp,
            unknownState);
        cache.remove(resourceId);
        // we propagate event only for our update or newer other can be discarded since we know we
        // will receive
        // additional event
        result = comp == 0 ? EventHandling.OBSOLETE : EventHandling.NEW;
      } else {
        result = EventHandling.OBSOLETE;
      }
    }
    var ed = activeUpdates.get(resourceId);
    if (ed != null && result != EventHandling.OBSOLETE) {
      log.debug("Setting last event for id: {} delete: {}", resourceId, delete);
      ed.setLastEvent(
          delete
              ? new ResourceDeleteEvent(ResourceAction.DELETED, resourceId, resource, unknownState)
              : new ExtendedResourceEvent(action, resourceId, resource, prevResourceVersion));
      return EventHandling.DEFER;
    } else {
      return result;
    }
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

    // todo unit test
    // this can happen when we dynamically change the NS
    if (!managedInformerEventSource
        .manager()
        .isWatchingNamespace(newResource.getMetadata().getNamespace())) {
      return;
    }

    // check against the latestResourceVersion processed by the TemporaryResourceCache
    // If the resource is older, then we can safely ignore.
    //
    // this also prevents resurrecting recently deleted entities for which the delete event
    // has already been processed
    var latestRV = getLastSyncResourceVersion(newResource.getMetadata().getNamespace());
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

    // also make sure that we're later than the existing temporary entry
    var cachedResource = getResourceFromCache(resourceId).orElse(null);

    if (cachedResource == null
        || ReconcilerUtilsInternal.compareResourceVersions(newResource, cachedResource) > 0) {
      log.debug(
          "Temporarily moving ahead to target version {} for resource id: {}",
          newResource.getMetadata().getResourceVersion(),
          resourceId);
      cache.put(resourceId, newResource);
    }
  }

  private String getLastSyncResourceVersion(String namespace) {
    return managedInformerEventSource.manager().lastSyncResourceVersion(namespace);
  }

  // todo tests with combination of event processing
  /**
   * There are (probably extremely rare) circumstances, when we can miss a delete event related to a
   * resources: when we create a resource that is deleted right after by third party and the related
   * informer have a disconnected watch and this watch needs to do a re-list when connected again.
   * In this case neither the ADD nor DELETE event will be propagated to the informer, but we
   * explicitly add resources to this cache. Those are cleaned up by this check.
   */
  private void checkGhostResources() {
    log.debug("Checking for ghost resources.");
    var iterator = cache.entrySet().iterator();
    while (iterator.hasNext()) {
      var e = iterator.next();

      var ns = e.getValue().getMetadata().getNamespace();
      // todo unit tests
      // this can happen if followed namespaces are changed dynamically
      if (!managedInformerEventSource.manager().isWatchingNamespace(ns)) {
        log.debug(
            "Removing resource: {} from cache as part of ghost cleanup. Namespace is not followed"
                + " anymore: {}",
            e.getKey(),
            ns);
        iterator.remove();
        continue;
      }
      if ((ReconcilerUtilsInternal.compareResourceVersions(
                  e.getValue().getMetadata().getResourceVersion(), getLastSyncResourceVersion(ns))
              < 0)
          // making sure we have the situation where resource is missing from the cache
          && managedInformerEventSource
              .manager()
              .get(ResourceID.fromResource(e.getValue()))
              .isEmpty()) {
        iterator.remove();
        managedInformerEventSource.handleEvent(ResourceAction.DELETED, e.getValue(), null, true);
        log.debug("Removing ghost resource with ID: {}", e.getKey());
      }
    }
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }
}
