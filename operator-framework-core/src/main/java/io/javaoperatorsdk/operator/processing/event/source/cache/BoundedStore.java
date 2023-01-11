package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class BoundedStore<K, R> {

  protected final ResourceFetcher<K, R> resourceFetcher;
  protected final BoundedCache<K, R> cache;
  protected Set<K> existingResources = Collections.synchronizedSet(new HashSet<>());;

  public BoundedStore(ResourceFetcher<K, R> resourceFetcher, BoundedCache<K, R> cache) {
    this.resourceFetcher = resourceFetcher;
    this.cache = cache;
  }

  public R get(K key) {
    var res = cache.get(key);
    if (res != null) {
      return res;
    }
    if (!existingResources.contains(key)) {
      return null;
    } else {
      return fetchAndCacheResource(key);
    }
  }

  public R remove(K key) {
    existingResources.remove(key);
    return cache.remove(key);
  }

  public R put(K key, R object) {
    var res = cache.put(key, object);
    existingResources.add(key);
    return res;
  }

  public Stream<K> keys() {
    return existingResources.stream();
  }

  protected R fetchAndCacheResource(K key) {
    var newRes = resourceFetcher.fetchResource(key);
    cache.put(key, newRes);
    return newRes;
  }

}
