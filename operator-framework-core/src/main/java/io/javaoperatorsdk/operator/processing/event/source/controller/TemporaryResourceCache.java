package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class TemporaryResourceCache<T extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(TemporaryResourceCache.class);

  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();
  private final ControllerResourceCache<T> managedInformerEventSource;

  public TemporaryResourceCache(ControllerResourceCache<T> managedInformerEventSource) {
    this.managedInformerEventSource = managedInformerEventSource;
  }

  public void removeResourceFromCache(T resource) {
    lock.lock();
    try {
      cache.remove(ResourceID.fromResource(resource));
    } finally {
      lock.unlock();
    }
  }

  public void putUpdatedResource(T newResource, String previousResourceVersion) {
    lock.lock();
    try {
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

