package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * <p>
 * Temporal cache is used to solve the problem for {@link KubernetesDependentResource} that is, when
 * a create or update is executed the subsequent getResource opeeration might not return the
 * up-to-date resource from informer cache, since it is not received yet by webhook.
 * </p>
 * <p>
 * The idea of the solution is, that since an update (for create is simpler) was done successfully,
 * and optimistic locking is in place, there were no other operations between reading the resource
 * from the cache and the actual update. So when the new resource is stored in the temporal cache
 * only if the informer still has the previous resource version, from before the update. If not,
 * that means there were already updates on the cache (either by the actual update from
 * DependentResource or other) so the resource does not needs to be cached. Subsequently if event
 * received from the informer, it means that the cache of the informer was updated, so it already
 * contains a more fresh version of the resource.
 * </p>
 *
 * @param <T> resource to cache.
 */
public class TemporaryResourceCache<T extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(TemporaryResourceCache.class);
  private static final int MAX_RESOURCE_VERSIONS = 256;

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();

  private final Map<ResourceID, List<String>> tombstones =
      new ConcurrentHashMap<ResourceID, List<String>>();
  private final ManagedInformerEventSource<T, ?, ?> managedInformerEventSource;
  private final boolean parseResourceVersions;
  private final Set<String> knownResourceVersions;

  public TemporaryResourceCache(ManagedInformerEventSource<T, ?, ?> managedInformerEventSource,
      boolean parseResourceVersions) {
    this.managedInformerEventSource = managedInformerEventSource;
    this.parseResourceVersions = parseResourceVersions;
    if (parseResourceVersions) {
      knownResourceVersions = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
          return size() >= MAX_RESOURCE_VERSIONS;
        }
      });
    } else {
      knownResourceVersions = null;
    }
  }

  public void prepareForAddOrUpdate(ResourceID id) {
    tombstones.put(id, new ArrayList<>());
  }

  public void finshedAddOrUpdate(ResourceID id) {
    tombstones.remove(id);
  }

  public synchronized void onDeleteEvent(T resource, boolean unknownState) {
    tombstones.computeIfPresent(ResourceID.fromResource(resource), (k, v) -> {
      v.add(resource.getMetadata().getUid());
      return v;
    });
    onEvent(resource, unknownState);
  }

  public synchronized void onEvent(T resource, boolean unknownState) {
    cache.computeIfPresent(ResourceID.fromResource(resource),
        (id, cached) -> (unknownState || !isLaterResourceVersion(id, cached, resource)) ? null
            : cached);
  }

  public synchronized void putAddedResource(T newResource) {
    putResource(newResource, null);
  }

  /**
   * put the item into the cache if the previousResourceVersion matches the current state. If not
   * the currently cached item is removed.
   *
   * @param previousResourceVersion null indicates an add
   */
  public synchronized void putResource(T newResource, String previousResourceVersion) {
    if (knownResourceVersions != null) {
      knownResourceVersions.add(newResource.getMetadata().getResourceVersion());
    }
    var resourceId = ResourceID.fromResource(newResource);
    var cachedResource = getResourceFromCache(resourceId)
        .orElse(managedInformerEventSource.get(resourceId).orElse(null));

    boolean moveAhead = false;
    if (previousResourceVersion == null && cachedResource == null) {
      if (Optional.ofNullable(tombstones.get(resourceId))
          .filter(list -> list.contains(newResource.getMetadata().getUid())).isPresent()) {
        log.debug(
            "Won't resurrect uid {} for resource id: {}",
            newResource.getMetadata().getUid(), resourceId);
        return;
      }
      // we can skip further checks as this is a simple add and there's no previous entry to consider
      moveAhead = true;
    }

    if (moveAhead
        || (cachedResource != null
            && (cachedResource.getMetadata().getResourceVersion().equals(previousResourceVersion))
            || isLaterResourceVersion(resourceId, newResource, cachedResource))) {
      log.debug(
          "Temporarily moving ahead to target version {} for resource id: {}",
          newResource.getMetadata().getResourceVersion(), resourceId);
      cache.put(resourceId, newResource);
    } else if (cache.remove(resourceId) != null) {
      log.debug("Removed an obsolete resource from cache for id: {}", resourceId);
    }
  }

  public boolean isKnownResourceVersion(T resource) {
    return knownResourceVersions != null
        && knownResourceVersions.contains(resource.getMetadata().getResourceVersion());
  }

  /**
   * @return true if {@link InformerConfiguration#parseResourceVersions()} is enabled and the
   *         resourceVersion of newResource is numerically greater than cachedResource, otherwise
   *         false
   */
  private boolean isLaterResourceVersion(ResourceID resourceId, T newResource, T cachedResource) {
    try {
      if (parseResourceVersions
          && Long.parseLong(newResource.getMetadata().getResourceVersion()) > Long
              .parseLong(cachedResource.getMetadata().getResourceVersion())) {
        return true;
      }
    } catch (NumberFormatException e) {
      log.debug(
          "Could not compare resourceVersions {} and {} for {}",
          newResource.getMetadata().getResourceVersion(),
          cachedResource.getMetadata().getResourceVersion(), resourceId);
    }
    return false;
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }
}
