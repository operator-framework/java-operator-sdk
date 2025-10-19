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
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Temporal cache is used to solve the problem for {@link KubernetesDependentResource} that is, when
 * a create or update is executed the subsequent getResource operation might not return the
 * up-to-date resource from informer cache, since it is not received yet.
 *
 * <p>The idea of the solution is, that since an update (for create is simpler) was done
 * successfully, and optimistic locking is in place, there were no other operations between reading
 * the resource from the cache and the actual update. So when the new resource is stored in the
 * temporal cache only if the informer still has the previous resource version, from before the
 * update. If not, that means there were already updates on the cache (either by the actual update
 * from DependentResource or other) so the resource does not needs to be cached. Subsequently if
 * event received from the informer, it means that the cache of the informer was updated, so it
 * already contains a more fresh version of the resource.
 *
 * @param <T> resource to cache.
 */
public class TemporaryResourceCache<T extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(TemporaryResourceCache.class);

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();

  private final ManagedInformerEventSource<T, ?, ?> managedInformerEventSource;
  private final boolean parseResourceVersions;

  public TemporaryResourceCache(
      ManagedInformerEventSource<T, ?, ?> managedInformerEventSource,
      boolean parseResourceVersions) {
    this.managedInformerEventSource = managedInformerEventSource;
    this.parseResourceVersions = parseResourceVersions;
  }

  public synchronized void onDeleteEvent(T resource, boolean unknownState) {
    onEvent(resource, unknownState);
  }

  public synchronized void onAddOrUpdateEvent(T resource) {
    onEvent(resource, false);
  }

  synchronized void onEvent(T resource, boolean unknownState) {
    cache.computeIfPresent(
        ResourceID.fromResource(resource),
        (id, cached) ->
            (unknownState
                    || PrimaryUpdateAndCacheUtils.compareResourceVersions(resource, cached) > 0)
                ? null
                : cached);
  }

  /**
   * put the item into the cache if the previousResourceVersion matches the current state. If not
   * the currently cached item is removed.
   */
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

    // first check against the source in general - this also prevents resurrecting resources when
    // we've already seen the deletion event
    String latest =
        managedInformerEventSource
            .getLastSyncResourceVersion(resourceId.getNamespace())
            .orElse(null);
    if (latest != null
        && PrimaryUpdateAndCacheUtils.compareResourceVersions(
                latest, newResource.getMetadata().getResourceVersion())
            > 0) {
      log.debug(
          "Resource {}: resourceVersion {} is not later than latest {}",
          resourceId,
          newResource.getMetadata().getResourceVersion(),
          latest);
      return;
    }

    var cachedResource = managedInformerEventSource.get(resourceId).orElse(null);

    if (cachedResource == null
        || PrimaryUpdateAndCacheUtils.compareResourceVersions(newResource, cachedResource) >= 0) {
      log.debug(
          "Temporarily moving ahead to target version {} for resource id: {}",
          newResource.getMetadata().getResourceVersion(),
          resourceId);
      cache.put(resourceId, newResource);
    }
  }

  public boolean canSkipEvent(ResourceID resourceID, T resource) {
    return parseResourceVersions
        && getResourceFromCache(resourceID)
            .filter(
                cached -> PrimaryUpdateAndCacheUtils.compareResourceVersions(cached, resource) >= 0)
            .isPresent();
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }
}
