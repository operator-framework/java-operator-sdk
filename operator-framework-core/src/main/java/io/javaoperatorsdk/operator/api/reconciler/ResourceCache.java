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
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.Cache;

@SuppressWarnings("unchecked")
public interface ResourceCache<T extends HasMetadata> extends Cache<T> {

  /**
   * Lists all resources in the given namespace.
   *
   * @param namespace the namespace to list resources from
   * @return a stream of all cached resources in the namespace
   */
  default Stream<T> list(String namespace) {
    return list(namespace, TRUE);
  }

  /**
   * Lists resources in the given namespace that match the provided predicate.
   *
   * @param namespace the namespace to list resources from
   * @param predicate filter to apply on the resources
   * @return a stream of cached resources matching the predicate
   */
  Stream<T> list(String namespace, Predicate<T> predicate);
}
