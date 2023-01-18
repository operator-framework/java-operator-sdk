package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.util.Set;

// todo: rename to cache? (replace the old one)
public interface BoundedCache<K, R> {

  R get(K key);

  R remove(K key);

  R put(K key, R object);

  Set<K> keys();

  Set<R> values();

}
