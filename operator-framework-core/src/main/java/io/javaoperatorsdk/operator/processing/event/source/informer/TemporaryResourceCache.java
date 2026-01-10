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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
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
  private final boolean comparableResourceVersions;
  private String latestResourceVersion;

  private final Map<ResourceID, EventFilterDetails> activeUpdates = new HashMap<>();

  public enum EventHandling {
    DEFER,
    OBSOLETE,
    NEW
  }

  public TemporaryResourceCache(boolean comparableResourceVersions) {
    this.comparableResourceVersions = comparableResourceVersions;
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
    if (ed.decreaseActiveUpdates()) {
      activeUpdates.remove(resourceID);
      return ed.getLatestEventAfterLastUpdateEvent(updatedResourceVersion);
    } else {
      return Optional.empty();
    }
  }

  public void onDeleteEvent(T resource, boolean unknownState) {
    onEvent(resource, unknownState, true);
  }

  /**
   * @return true if the resourceVersion was obsolete
   */
  public EventHandling onAddOrUpdateEvent(T resource) {
    return onEvent(resource, false, false);
  }

  private synchronized EventHandling onEvent(T resource, boolean unknownState, boolean delete) {
    if (!comparableResourceVersions) {
      return EventHandling.NEW;
    }

    var resourceId = ResourceID.fromResource(resource);
    if (log.isDebugEnabled()) {
      log.debug(
          "Processing event for resource id: {} version: {} ",
          resourceId,
          resource.getMetadata().getResourceVersion());
    }
    if (!unknownState) {
      latestResourceVersion = resource.getMetadata().getResourceVersion();
    }
    var cached = cache.get(resourceId);
    EventHandling result = EventHandling.NEW;
    int comp = 0;
    if (cached != null) {
      comp = ReconcileUtils.validateAndCompareResourceVersions(resource, cached);
      if (comp >= 0 || unknownState) {
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
      ed.setLastEvent(
          delete
              ? new ResourceDeleteEvent(ResourceAction.DELETED, resourceId, resource, unknownState)
              : new ResourceEvent(
                  ResourceAction.UPDATED, resourceId, resource)); // todo true action
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

    // check against the latestResourceVersion processed by the TemporaryResourceCache
    // If the resource is older, then we can safely ignore.
    //
    // this also prevents resurrecting recently deleted entities for which the delete event
    // has already been processed
    if (latestResourceVersion != null
        && ReconcileUtils.compareResourceVersions(
                latestResourceVersion, newResource.getMetadata().getResourceVersion())
            > 0) {
      log.debug(
          "Resource {}: resourceVersion {} is not later than latest {}",
          resourceId,
          newResource.getMetadata().getResourceVersion(),
          latestResourceVersion);
      return;
    }

    // also make sure that we're later than the existing temporary entry
    var cachedResource = getResourceFromCache(resourceId).orElse(null);

    if (cachedResource == null
        || ReconcileUtils.validateAndCompareResourceVersions(newResource, cachedResource) > 0) {
      log.debug(
          "Temporarily moving ahead to target version {} for resource id: {}",
          newResource.getMetadata().getResourceVersion(),
          resourceId);
      cache.put(resourceId, newResource);
    }
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }
}
