package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
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
 * @param <R> represents the type of resources (usually external non-kubernetes ones) being handled.
 */
public abstract class CachingEventSource<R, P extends HasMetadata>
    extends AbstractResourceEventSource<P, R> implements Cache<R> {

  protected UpdatableCache<R> cache;

  protected CachingEventSource(Class<R> resourceClass) {
    super(resourceClass);
    cache = initCache();
  }

  @Override
  public Optional<R> get(ResourceID resourceID) {
    return cache.get(resourceID);
  }

  @Override
  public boolean contains(ResourceID resourceID) {
    return cache.contains(resourceID);
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.keys();
  }

  @Override
  public Stream<R> list(Predicate<R> predicate) {
    return cache.list(predicate);
  }

  protected void handleDelete(ResourceID relatedResourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    cache.remove(relatedResourceID);
    // we only propagate event if the resource was previously in cache
    if (cachedValue.isPresent()) {
      getEventHandler().handleEvent(new Event(relatedResourceID));
    }
  }

  protected void handleEvent(R value, ResourceID relatedResourceID) {
    if (!isRunning()) {
      return;
    }
    var cachedValue = cache.get(relatedResourceID);
    if (cachedValue.map(v -> !v.equals(value)).orElse(true)) {
      cache.put(relatedResourceID, value);
      getEventHandler().handleEvent(new Event(relatedResourceID));
    }
  }

  protected UpdatableCache<R> initCache() {
    return new ConcurrentHashMapCache<>();
  }

  public Optional<R> getCachedValue(ResourceID resourceID) {
    return cache.get(resourceID);
  }

  @Override
  public Optional<R> getAssociated(P primary) {
    return cache.get(ResourceID.fromResource(primary));
  }

}
