package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
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

  // keep up to the last million deletions for up to 10 minutes
  private final ExpirationCache<String> tombstones = new ExpirationCache<>(1000000, 1200000);
  private final ManagedInformerEventSource<T, ?, ?> managedInformerEventSource;
  private final boolean parseResourceVersions;
  private final ExpirationCache<String> knownResourceVersions;
  private final TemporalPrimaryToSecondaryIndex<T> temporalPrimaryToSecondaryIndex;

  public TemporaryResourceCache(
      ManagedInformerEventSource<T, ?, ?> managedInformerEventSource,
      TemporalPrimaryToSecondaryIndex<T> temporalPrimaryToSecondaryIndex,
      boolean parseResourceVersions) {
    this.managedInformerEventSource = managedInformerEventSource;
    this.parseResourceVersions = parseResourceVersions;
    this.temporalPrimaryToSecondaryIndex = temporalPrimaryToSecondaryIndex;
    if (parseResourceVersions) {
      // keep up to the 50000 add/updates for up to 5 minutes
      knownResourceVersions = new ExpirationCache<>(50000, 600000);
    } else {
      knownResourceVersions = null;
    }
  }

  public synchronized void onDeleteEvent(T resource, boolean unknownState) {
    tombstones.add(resource.getMetadata().getUid());
    onEvent(resource, unknownState);
  }

  public synchronized void onAddOrUpdateEvent(T resource) {
    onEvent(resource, false);
  }

  synchronized void onEvent(T resource, boolean unknownState) {
    var res =
        cache.computeIfPresent(
            ResourceID.fromResource(resource),
            (id, cached) ->
                (unknownState || !isLaterResourceVersion(id, cached, resource)) ? null : cached);
    if (res == null) {
      temporalPrimaryToSecondaryIndex.cleanupForResource(resource);
    }
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
    var cachedResource = managedInformerEventSource.get(resourceId).orElse(null);

    boolean moveAhead = false;
    if (previousResourceVersion == null && cachedResource == null) {
      if (tombstones.contains(newResource.getMetadata().getUid())) {
        log.debug(
            "Won't resurrect uid {} for resource id: {}",
            newResource.getMetadata().getUid(),
            resourceId);
        return;
      }
      // we can skip further checks as this is a simple add and there's no previous entry to
      // consider
      moveAhead = true;
    }
    if (moveAhead
        || (cachedResource != null
                && (cachedResource
                    .getMetadata()
                    .getResourceVersion()
                    .equals(previousResourceVersion))
            || isLaterResourceVersion(resourceId, newResource, cachedResource))) {
      log.debug(
          "Temporarily moving ahead to target version {} for resource id: {}",
          newResource.getMetadata().getResourceVersion(),
          resourceId);
      temporalPrimaryToSecondaryIndex.explicitAddOrUpdate(newResource);
      cache.put(resourceId, newResource);
    } else if (cache.remove(resourceId) != null) {
      log.debug("Removed an obsolete resource from cache for id: {}", resourceId);
    }
  }

  public synchronized boolean isKnownResourceVersion(T resource) {
    return knownResourceVersions != null
        && knownResourceVersions.contains(resource.getMetadata().getResourceVersion());
  }

  /**
   * @return true if {@link ConfigurationService#parseResourceVersionsForEventFilteringAndCaching()}
   *     is enabled and the resourceVersion of newResource is numerically greater than
   *     cachedResource, otherwise false
   */
  private boolean isLaterResourceVersion(ResourceID resourceId, T newResource, T cachedResource) {
    try {
      if (parseResourceVersions
          && Long.parseLong(newResource.getMetadata().getResourceVersion())
              > Long.parseLong(cachedResource.getMetadata().getResourceVersion())) {
        return true;
      }
    } catch (NumberFormatException e) {
      log.debug(
          "Could not compare resourceVersions {} and {} for {}",
          newResource.getMetadata().getResourceVersion(),
          cachedResource.getMetadata().getResourceVersion(),
          resourceId);
    }
    return false;
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  public TemporalPrimaryToSecondaryIndex<T> getTemporalPrimaryToSecondaryIndex() {
    return temporalPrimaryToSecondaryIndex;
  }

  static class ExpirationCache<K> {
    private final LinkedHashMap<K, Long> cache;
    private final int ttlMs;

    public ExpirationCache(int maxEntries, int ttlMs) {
      this.ttlMs = ttlMs;
      this.cache =
          new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Long> eldest) {
              return size() > maxEntries;
            }
          };
    }

    public void add(K key) {
      clean();
      cache.putIfAbsent(key, System.currentTimeMillis());
    }

    public boolean contains(K key) {
      clean();
      return cache.get(key) != null;
    }

    void clean() {
      if (!cache.isEmpty()) {
        long currentTimeMillis = System.currentTimeMillis();
        var iter = cache.entrySet().iterator();
        // the order will already be from oldest to newest, clean a fixed number of entries to
        // amortize the cost amongst multiple calls
        for (int i = 0; i < 10 && iter.hasNext(); i++) {
          var entry = iter.next();
          if (currentTimeMillis - entry.getValue() > ttlMs) {
            iter.remove();
          }
        }
      }
    }
  }
}
