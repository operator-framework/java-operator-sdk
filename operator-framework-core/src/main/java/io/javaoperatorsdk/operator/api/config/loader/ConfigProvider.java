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
package io.javaoperatorsdk.operator.api.config.loader;

import java.util.Optional;

public interface ConfigProvider {

  /**
   * Returns the value associated with {@code key}, converted to {@code type}, or an empty {@link
   * Optional} if the key is not set.
   *
   * @param key the dot-separated configuration key, e.g. {@code josdk.cache.sync.timeout}
   * @param type the expected type of the value; supported types depend on the implementation
   * @param <T> the value type
   * @return an {@link Optional} containing the typed value, or empty if the key is absent
   * @throws IllegalArgumentException if {@code type} is not supported by the implementation
   */
  <T> Optional<T> getValue(String key, Class<T> type);
}
