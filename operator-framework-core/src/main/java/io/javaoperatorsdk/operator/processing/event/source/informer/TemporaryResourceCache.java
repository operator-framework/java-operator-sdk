package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
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

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final ManagedInformerEventSource<T, ?, ?> managedInformerEventSource;

  public TemporaryResourceCache(ManagedInformerEventSource<T, ?, ?> managedInformerEventSource) {
    this.managedInformerEventSource = managedInformerEventSource;
  }

  public synchronized void removeResourceFromCache(T resource) {
    cache.remove(ResourceID.fromResource(resource));
  }

  public synchronized void unconditionallyCacheResource(T newResource) {
    cache.put(ResourceID.fromResource(newResource), newResource);
  }

  public synchronized void putAddedResource(T newResource) {
    ResourceID resourceID = ResourceID.fromResource(newResource);
    if (managedInformerEventSource.get(resourceID).isEmpty()) {
      log.debug("Putting resource to cache with ID: {}", resourceID);
      cache.put(resourceID, newResource);
    } else {
      log.debug("Won't put resource into cache found already informer cache: {}", resourceID);
    }
  }

  public synchronized void putUpdatedResource(T newResource, String previousResourceVersion) {
    var resourceId = ResourceID.fromResource(newResource);
    var informerCacheResource = managedInformerEventSource.get(resourceId);
    if (informerCacheResource.isEmpty()) {
      log.debug("No cached value present for resource: {}", newResource);
      return;
    }
    // if this is not true that means the cache was already updated
    if (informerCacheResource.get().getMetadata().getResourceVersion()
        .equals(previousResourceVersion)) {
      log.debug("Putting resource to temporal cache with id: {}", resourceId);
      cache.put(resourceId, newResource);
    } else {
      // if something is in cache it's surely obsolete now
      log.debug("Trying to remove an obsolete resource from cache for id: {}", resourceId);
      cache.remove(resourceId);
    }
  }

  public synchronized Optional<T> getResourceFromCache(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }
}
