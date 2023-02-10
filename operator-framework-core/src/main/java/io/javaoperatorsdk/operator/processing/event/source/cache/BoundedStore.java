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
      return fetchAndCacheResourceIfStillNonPresent(key);
    }
  }

  public synchronized R remove(K key) {
    existingResources.remove(key);
    return cache.remove(key);
  }

  public synchronized R put(K key, R object) {
    var res = cache.put(key, object);
    existingResources.add(key);
    return res;
  }

  public Stream<K> keys() {
    return existingResources.stream();
  }

  protected R fetchAndCacheResourceIfStillNonPresent(K key) {
    var newRes = resourceFetcher.fetchResource(key);
    // todo unit test
    // Just want to put the fetched resource if there is still no resource published from different
    // source.
    // In case of informers actually multiple events might arrive, therefore non fetched resources
    // should
    // take always precedence.
    synchronized (this) {
      var actual = cache.get(key);
      if (actual == null) {
        cache.put(key, newRes);
        return newRes;
      } else {
        return actual;
      }
    }
  }
}
