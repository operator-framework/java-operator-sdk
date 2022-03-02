package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ConcurrentHashMapCache<T> implements UpdatableCache<T> {
  private final Map<ResourceID, T> cache = new ConcurrentHashMap<>();

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return Optional.ofNullable(cache.get(resourceID));
  }

  @Override
  public Stream<ResourceID> keys() {
    return cache.keySet().stream();
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return cache.values().stream().filter(predicate);
  }

  @Override
  public T remove(ResourceID key) {
    return cache.remove(key);
  }

  @Override
  public void put(ResourceID key, T resource) {
    cache.put(key, resource);
  }
}
