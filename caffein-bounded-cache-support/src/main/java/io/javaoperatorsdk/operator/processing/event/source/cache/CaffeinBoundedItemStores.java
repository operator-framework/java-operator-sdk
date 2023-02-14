package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class CaffeinBoundedItemStores {

  private CaffeinBoundedItemStores() {}

  public static <R extends HasMetadata> BoundedItemStore<R> boundedItemStore(
      KubernetesClient client, Class<R> rClass,
      Duration accessExpireDuration, long cacheMaxSize) {
    Cache<String, R> cache = Caffeine.newBuilder()
        .expireAfterAccess(accessExpireDuration)
        .maximumSize(cacheMaxSize)
        .build();
    return boundedItemStore(client, rClass, cache);
  }

  public static <R extends HasMetadata> BoundedItemStore<R> boundedItemStore(
      KubernetesClient client, Class<R> rClass,
      Duration accessExpireDuration) {
    Cache<String, R> cache = Caffeine.newBuilder()
        .expireAfterAccess(accessExpireDuration)
        .build();
    return boundedItemStore(client, rClass, cache);
  }

  public static <R extends HasMetadata> BoundedItemStore<R> boundedItemStore(
      KubernetesClient client, Class<R> rClass, Cache<String, R> cache) {
    return new BoundedItemStore<>(client,
        new CaffeinBoundedCache<>(cache), rClass);
  }

}
