package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Base class for event sources with caching capabilities.
 * <p>
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
