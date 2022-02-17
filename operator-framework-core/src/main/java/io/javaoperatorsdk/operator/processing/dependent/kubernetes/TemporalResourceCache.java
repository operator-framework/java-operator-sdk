package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

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
 * contains a more fresh version of the resource. (See one caveat below)
 * </p>
 * 
 * @param <T> resource to cache.
 */
public class TemporalResourceCache<T extends HasMetadata> implements ResourceEventHandler<T> {

  private static final Logger log = LoggerFactory.getLogger(TemporalResourceCache.class);

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();
  private final InformerEventSource<T, ?> informerEventSource;

  public TemporalResourceCache(InformerEventSource<T, ?> informerEventSource) {
    this.informerEventSource = informerEventSource;
  }

  @Override
  public void onAdd(T t) {
    removeResourceFromCache(t);
  }

  /**
   * In theory, it can happen that an older event is received, that is received before we updated
   * the resource. So that is a situation still not covered, but it happens with extremely low
   * probability and since it should trigger a new reconciliation, eventually the system is
   * consistent.
   * 
   * @param t old object
   * @param t1 new object
   */
  @Override
  public void onUpdate(T t, T t1) {
    removeResourceFromCache(t1);
  }

  @Override
  public void onDelete(T t, boolean b) {
    removeResourceFromCache(t);
  }

  private void removeResourceFromCache(T resource) {
    lock.lock();
    try {
      cache.remove(ResourceID.fromResource(resource));
    } finally {
      lock.unlock();
    }
  }

  public void putAddedResource(T newResource) {
    lock.lock();
    try {
      if (informerEventSource.get(ResourceID.fromResource(newResource)).isEmpty()) {
        cache.put(ResourceID.fromResource(newResource), newResource);
      }
    } finally {
      lock.unlock();
    }
  }

  public void putUpdatedResource(T newResource, String previousResourceVersion) {
    lock.lock();
    try {
      var resourceId = ResourceID.fromResource(newResource);
      var informerCacheResource = informerEventSource.get(resourceId);
      if (informerCacheResource.isEmpty()) {
        log.debug("No cached value present for resource: {}", newResource);
        return;
      }
      // if this is not true that means the cache was already updated
      if (informerCacheResource.get().getMetadata().getResourceVersion()
          .equals(previousResourceVersion)) {
        cache.put(resourceId, newResource);
      } else {
        // if something is in cache it's surely obsolete now
        cache.remove(resourceId);
      }
    } finally {
      lock.unlock();
    }
  }

  public Optional<T> getResourceFromCache(ResourceID resourceID) {
    try {
      lock.lock();
      return Optional.ofNullable(cache.get(resourceID));
    } finally {
      lock.unlock();
    }
  }
}
