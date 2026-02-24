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
package io.javaoperatorsdk.operator.config.loader;

import java.util.function.BiConsumer;

/**
 * Associates a configuration key and its expected type with the setter that should be called on an
 * overrider when the {@link ConfigProvider} returns a value for that key.
 *
 * @param <O> the overrider type (e.g. {@code ConfigurationServiceOverrider})
 * @param <T> the value type expected for this key
 */
public class ConfigBinding<O, T> {

  private final String key;
  private final Class<T> type;
  private final BiConsumer<O, T> setter;

  public ConfigBinding(String key, Class<T> type, BiConsumer<O, T> setter) {
    this.key = key;
    this.type = type;
    this.setter = setter;
  }

  public String key() {
    return key;
  }

  public Class<T> type() {
    return type;
  }

  public BiConsumer<O, T> setter() {
    return setter;
  }
}
