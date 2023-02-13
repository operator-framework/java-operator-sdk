package io.javaoperatorsdk.operator.processing.event.source.cache;

// todo: rename to cache? (replace the old one)
public interface BoundedCache<K, R> {

  R get(K key);

  R remove(K key);

  R put(K key, R object);

}
