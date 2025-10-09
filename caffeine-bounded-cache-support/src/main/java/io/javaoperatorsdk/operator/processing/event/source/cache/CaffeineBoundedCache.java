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

import com.github.benmanes.caffeine.cache.Cache;

/** Caffeine cache wrapper to be used in a {@link BoundedItemStore} */
public class CaffeineBoundedCache<K, R> implements BoundedCache<K, R> {

  private final Cache<K, R> cache;

  public CaffeineBoundedCache(Cache<K, R> cache) {
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
