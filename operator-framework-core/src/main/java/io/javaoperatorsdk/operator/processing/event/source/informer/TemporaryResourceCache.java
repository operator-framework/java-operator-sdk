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
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Temporal cache is used to solve the problem for {@link KubernetesDependentResource} that is, when
 * a create or update is executed the subsequent getResource operation might not return the
 * up-to-date resource from informer cache, since it is not received yet.
 *
 * <p>Since an update (for create is simpler) was done successfully we can temporarily track 
 * that resource if its version is later than the events we've processed. We then know that we can skip
 * all events that have the same resource version or earlier than the tracked resource. Once we process 
 * an event that has the same resource version or later, then we know the tracked resource can be removed.
 * 
 * <p>In some cases it is possible for the informer to deliver events prior to the attempt to put the
 * resource in the temporal cache. The startModifying/doneModifying methods are used to pause event
 * delivery to ensure that temporal cache recognizes the put entry as an event that can be skipped.
 * 
 * <p>If comparable resource versions are disabled, then this cache is effectively disabled.
 *
 * @param <T> resource to cache.
 */
public class TemporaryResourceCache<T extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(TemporaryResourceCache.class);

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final boolean parseResourceVersions;
  private final Map<ResourceID, ReentrantLock> activelyModifying = new ConcurrentHashMap<>();
  private String latestResourceVersion;

  public TemporaryResourceCache(boolean parseResourceVersions) {
    this.parseResourceVersions = parseResourceVersions;
  }

  public void startModifying(ResourceID id) {
    if (!parseResourceVersions) {
      return;
    }
    activelyModifying
        .compute(
            id,
            (ignored, lock) -> {
              if (lock != null) {
                throw new IllegalStateException(); // concurrent modifications to the same resource
                // not allowed - this could be relaxed if needed
              }
              return new ReentrantLock();
            })
        .lock();
  }

  public void doneModifying(ResourceID id) {
    if (!parseResourceVersions) {
      return;
    }
    activelyModifying.computeIfPresent(
        id,
        (ignored, lock) -> {
          lock.unlock();
          return null;
        });
  }

  public void onDeleteEvent(T resource, boolean unknownState) {
    onEvent(resource, unknownState);
  }

  /**
   * @return true if the resourceVersion was already known
   */
  public boolean onAddOrUpdateEvent(T resource) {
    return onEvent(resource, false);
  }

  private boolean onEvent(T resource, boolean unknownState) {
    ReentrantLock lock = activelyModifying.get(ResourceID.fromResource(resource));
    if (lock != null) {
      lock.lock(); // wait for the modification to finish
      lock.unlock(); // simply unlock as the event is guaranteed after the modification
    }
    boolean[] known = new boolean[1];
    synchronized (this) {
      if (!unknownState) {
        latestResourceVersion = resource.getMetadata().getResourceVersion();
      }
      cache.computeIfPresent(
          ResourceID.fromResource(resource),
          (id, cached) -> {
            boolean remove = unknownState;
            if (!unknownState) {
              int comp = PrimaryUpdateAndCacheUtils.compareResourceVersions(resource, cached);
              if (comp >= 0) {
                remove = true;
              }
              if (comp <= 0) {
                known[0] = true;
              }
            }
            if (remove) {
              return null;
            }
            return cached;
          });
      return known[0];
    }
  }

  /** put the item into the cache if it's for a later state than what has already been observed. */
  public synchronized void putResource(T newResource) {
    if (!parseResourceVersions) {
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
        && PrimaryUpdateAndCacheUtils.compareResourceVersions(
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
        || PrimaryUpdateAndCacheUtils.compareResourceVersions(newResource, cachedResource) > 0) {
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
