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

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

public class DefaultConfigProvider implements ConfigProvider {

  private final Function<String, String> envLookup;

  public DefaultConfigProvider() {
    this(System::getenv);
  }

  DefaultConfigProvider(Function<String, String> envLookup) {
    this.envLookup = envLookup;
  }

  /**
   * Looks up {@code key} first as an environment variable (dots and hyphens replaced by
   * underscores, uppercased, e.g. {@code josdk.cache.sync.timeout} → {@code
   * JOSDK_CACHE_SYNC_TIMEOUT}), then as a system property with the key as-is. The environment
   * variable takes precedence when both are set.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> Optional<T> getValue(String key, Class<T> type) {
    String raw = resolveRaw(key);
    if (raw == null) {
      return Optional.empty();
    }
    return Optional.of(type.cast(convert(raw, type)));
  }

  private String resolveRaw(String key) {
    String envKey = key.replace('.', '_').replace('-', '_').toUpperCase();
    String envValue = envLookup.apply(envKey);
    if (envValue != null) {
      return envValue;
    }
    return System.getProperty(key);
  }

  private Object convert(String raw, Class<?> type) {
    if (type == String.class) {
      return raw;
    } else if (type == Boolean.class) {
      return Boolean.parseBoolean(raw);
    } else if (type == Integer.class) {
      return Integer.parseInt(raw);
    } else if (type == Long.class) {
      return Long.parseLong(raw);
    } else if (type == Duration.class) {
      return Duration.parse(raw);
    }
    throw new IllegalArgumentException("Unsupported config type: " + type.getName());
  }
}
