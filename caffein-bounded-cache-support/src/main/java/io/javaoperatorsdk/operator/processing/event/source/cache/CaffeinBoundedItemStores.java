package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * The idea about CaffeinBoundedItemStore-s is that, caffeine will cache the resources which were
 * recently used, and will evict resource, which are not used for a while. This is ideal from the
 * perspective that on startup controllers reconcile all resources (this is why a maxSize not ideal)
 * but after a while it can happen (well depending on the controller and domain) that only some
 * resources are actually active, thus related events happen. So in case large amount of custom
 * resources only the active once will remain in the cache. Note that if a resource is reconciled
 * all the secondary resources are usually reconciled too, in that case all those resources are
 * fetched and populated to the cache, and will remain there for some time, for a subsequent
 * reconciliations.
 */
public class CaffeinBoundedItemStores {

  private CaffeinBoundedItemStores() {}

  /**
   * @param client Kubernetes Client
   * @param rClass resource class
   * @param accessExpireDuration the duration after resources is evicted from cache if not accessed.
   * @return the ItemStore implementation
   * @param <R> resource type
   */
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
