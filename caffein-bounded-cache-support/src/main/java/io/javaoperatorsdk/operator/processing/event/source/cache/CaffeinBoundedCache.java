package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.util.HashSet;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;

public class CaffeinBoundedCache<K, R> implements BoundedCache<K, R> {

  private Cache<K, R> cache;

  public CaffeinBoundedCache(Cache<K, R> cache) {
    this.cache = cache;
  }

  @Override
  public R get(K key) {
    return cache.getIfPresent(key);
  }

  @Override
  public R remove(K key) {
    var value = cache.getIfPresent(key);
    cache.invalidate(key);
    return value;
  }

  @Override
  public R put(K key, R object) {
    cache.put(key, object);
    return object;
  }
}
