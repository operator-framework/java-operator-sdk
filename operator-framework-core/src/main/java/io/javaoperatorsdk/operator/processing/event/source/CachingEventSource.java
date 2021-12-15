package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;

import javax.cache.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger log = LoggerFactory.getLogger(CachingEventSource.class);

  protected Cache<ResourceID, T> cache;

  public CachingEventSource(Cache<ResourceID, T> cache) {
    this.cache = cache;
  }

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
    var cachedValue = cache.get(relatedResourceID);
    if (cachedValue == null || !cachedValue.equals(value)) {
      cache.put(relatedResourceID, value);
      eventHandler.handleEvent(new Event(relatedResourceID));
    }
  }

  public Cache<ResourceID, T> getCache() {
    return cache;
  }

  public Optional<T> getCachedValue(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  @Override
  public void stop() throws OperatorException {
    super.stop();
    cache.close();
  }
}
