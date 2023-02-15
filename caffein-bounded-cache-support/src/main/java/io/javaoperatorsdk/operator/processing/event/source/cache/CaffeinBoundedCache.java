package io.javaoperatorsdk.operator.processing.event.source.cache;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Caffein cache wrapper to be used in a {@link BoundedItemStore}
 * */
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
  public void put(K key, R object) {
    cache.put(key, object);
  }
}
