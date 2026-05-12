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
package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface Cache<T> {
  Predicate TRUE = (a) -> true;

  /**
   * Retrieves a resource from the cache by its {@link ResourceID}.
   *
   * @param resourceID the identifier of the resource
   * @return an Optional containing the resource if present in the cache
   */
  Optional<T> get(ResourceID resourceID);

  /**
   * Checks whether a resource with the given {@link ResourceID} exists in the cache.
   *
   * @param resourceID the identifier of the resource
   * @return {@code true} if the resource is present in the cache
   */
  default boolean contains(ResourceID resourceID) {
    return get(resourceID).isPresent();
  }

  /**
   * Returns a stream of all {@link ResourceID}s currently in the cache.
   *
   * @return a stream of resource identifiers
   */
  Stream<ResourceID> keys();

  /**
   * Lists all resources in the cache.
   *
   * @return a stream of all cached resources
   */
  default Stream<T> list() {
    return list(TRUE);
  }

  /**
   * Lists resources in the cache that match the provided predicate.
   *
   * @param predicate filter to apply on the resources
   * @return a stream of cached resources matching the predicate
   */
  Stream<T> list(Predicate<T> predicate);
}
