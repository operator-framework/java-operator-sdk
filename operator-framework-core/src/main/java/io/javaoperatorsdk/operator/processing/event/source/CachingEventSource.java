package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Base class for event sources with caching capabilities.
 * <p>
 * {@link #handleDelete(ResourceID)} - if the related resource is present in the cache it is removed
 * and event propagated. There is no event propagated if the resource is not in the cache.
 * <p>
 * {@link #handleEvent(Object, ResourceID)} - caches the resource if changed or missing. Propagates
 * an event if the resource is new or not equals to the one in the cache, and if accepted by the
 * filter if one is present.
 *
 * @param <T> represents the type of resources (usually external non-kubernetes ones) being handled.
 */
public abstract class CachingEventSource<T> extends LifecycleAwareEventSource {

  protected Map<ResourceID, T> cache = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();

  public CachingEventSource() {}

  protected void handleDelete(ResourceID relatedResourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    cache.remove(relatedResourceID);
    // we only propagate event if the resource was previously in cache
    if (cachedValue != null) {
      eventHandler.handleEvent(new Event(relatedResourceID));
    }
  }

  protected void handleEvent(T value, ResourceID relatedResourceID) {
    if (!isRunning()) {
      return;
    }
    lock.lock();
    try {
      var cachedValue = cache.get(relatedResourceID);
      if (cachedValue == null || !cachedValue.equals(value)) {
        cache.put(relatedResourceID, value);
        eventHandler.handleEvent(new Event(relatedResourceID));
      }
    } finally {
      lock.unlock();
    }
  }

  public Map<ResourceID, T> getCache() {
    return Collections.unmodifiableMap(cache);
  }

  public Optional<T> getCachedValue(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
  }
}
