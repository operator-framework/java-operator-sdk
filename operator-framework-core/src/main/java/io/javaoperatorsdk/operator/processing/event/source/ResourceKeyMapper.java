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

import io.javaoperatorsdk.operator.processing.dependent.ResourceIDProvider;

public interface ResourceKeyMapper<R, ID> {

  ID keyFor(R resource);

  /**
   * Used if a polling event source handles only single secondary resource. See also docs for:
   * {@link ExternalResourceCachingEventSource}
   *
   * @return static id mapper, all resources are mapped for same id.
   * @param <T> secondary resource type
   */
  static <T> ResourceKeyMapper<T, String> singleResourceKeyMapper() {
    return r -> "id";
  }

  static <T, ID> ResourceKeyMapper<T, ID> resourceIdProviderBasedMapper() {
    return (T r) -> {
      if (r instanceof ResourceIDProvider<?> resourceIDProvider) {
        return (ID) resourceIDProvider.id();
      } else {
        throw new IllegalArgumentException(
            "Resource is not instance of "
                + ResourceIDProvider.class.getSimpleName()
                + ": "
                + r.getClass().getName());
      }
    };
  }
}
