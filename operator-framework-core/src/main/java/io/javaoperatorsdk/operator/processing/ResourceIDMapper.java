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
package io.javaoperatorsdk.operator.processing;

import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

public interface ResourceIDMapper<R, ID> {

  ID idFor(R resource);

  /**
   * Can be used if a polling event source handles only single secondary resource and the id is
   * String. See also docs for: {@link ExternalResourceCachingEventSource}
   *
   * @return static id mapper, all resources are mapped for same id.
   * @param <T> secondary resource type
   */
  static <T> ResourceIDMapper<T, String> singleResourceCacheKeyMapper() {
    return r -> "id";
  }

  static <T, ID> ResourceIDMapper<T, ID> resourceIdProviderMapper() {
    return r -> {
      if (r instanceof ResourceIDProvider resourceIDProvider) {
        return (ID) resourceIDProvider.resourceId();
      } else {
        throw new IllegalStateException(
            "Resource does not implement ExternalDependentIDProvider: " + r.getClass());
      }
    };
  }
}
