package io.javaoperatorsdk.operator.processing.event.source.cache;

public interface BoundedCache<K, R> {

  R get(K key);

  R remove(K key);

  void put(K key, R object);
}
