package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public class ConcurrentHashMapCache<T> implements UpdatableCache<T> {
  private final Map<ObjectKey, T> cache = new ConcurrentHashMap<>();

  @Override
  public Optional<T> get(ObjectKey objectKey) {
    return Optional.ofNullable(cache.get(objectKey));
  }

  @Override
  public Stream<ObjectKey> keys() {
    return cache.keySet().stream();
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return cache.values().stream().filter(predicate);
  }

  @Override
  public T remove(ObjectKey key) {
    return cache.remove(key);
  }

  @Override
  public void put(ObjectKey key, T resource) {
    cache.put(key, resource);
  }
}
