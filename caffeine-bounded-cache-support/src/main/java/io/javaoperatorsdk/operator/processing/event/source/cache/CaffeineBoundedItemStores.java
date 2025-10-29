/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * A factory for <a href="https://github.com/ben-manes/caffeine">Caffeine</a>-backed {@link
 * BoundedItemStore}. The implementation uses a {@link CaffeineBoundedCache} to store resources and
 * progressively evict them if they haven't been used in a while. The idea about
 * CaffeinBoundedItemStore-s is that, caffeine will cache the resources which were recently used,
 * and will evict resource, which are not used for a while. This is ideal for startup performance
 * and efficiency when all resources should be cached to avoid undue load on the API server. This is
 * why setting a maximal cache size is not practical and the approach of evicting least recently
 * used resources was chosen. However, depending on controller implementations and domains, it could
 * happen that some / many of these resources are then seldom or even reconciled anymore. In that
 * situation, large amounts of memory might be consumed to cache resources that are never used
 * again.
 *
 * <p>Note that if a resource is reconciled and is not present anymore in cache, it will
 * transparently be fetched again from the API server. Similarly, since associated secondary
 * resources are usually reconciled too, they might need to be fetched and populated to the cache,
 * and will remain there for some time, for subsequent reconciliations.
 */
public class CaffeineBoundedItemStores {

  private CaffeineBoundedItemStores() {}

  /**
   * @param client Kubernetes Client
   * @param rClass resource class
   * @param accessExpireDuration the duration after resources is evicted from cache if not accessed.
   * @return the ItemStore implementation
   * @param <R> resource type
   */
  @SuppressWarnings("unused")
  public static <R extends HasMetadata> BoundedItemStore<R> boundedItemStore(
      KubernetesClient client, Class<R> rClass, Duration accessExpireDuration) {
    Cache<String, R> cache = Caffeine.newBuilder().expireAfterAccess(accessExpireDuration).build();
    return boundedItemStore(client, rClass, cache);
  }

  public static <R extends HasMetadata> BoundedItemStore<R> boundedItemStore(
      KubernetesClient client, Class<R> rClass, Cache<String, R> cache) {
    return new BoundedItemStore<>(new CaffeineBoundedCache<>(cache), rClass, client);
  }
}
