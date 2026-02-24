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

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link ConfigProvider} that resolves configuration values from environment variables and Java
 * system properties.
 *
 * <p>For a given key, lookup proceeds as follows:
 *
 * <ol>
 *   <li>The key is converted to an environment variable name by replacing dots and hyphens with
 *       underscores and converting to upper case (e.g. {@code josdk.cache-sync.timeout} → {@code
 *       JOSDK_CACHE_SYNC_TIMEOUT}). If an environment variable with that name is set, its value is
 *       used.
 *   <li>If no matching environment variable is found, the key is looked up as a Java system
 *       property (via {@link System#getProperty(String)}) using the original key name.
 * </ol>
 *
 * <p>Environment variables take precedence over system properties when both are set. Supported
 * value types are: {@link String}, {@link Boolean}, {@link Integer}, {@link Long}, {@link Double},
 * and {@link java.time.Duration} (ISO-8601 format, e.g. {@code PT30S}).
 */
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
  public <T> Optional<T> getValue(String key, Class<T> type) {
    String raw = resolveRaw(key);
    if (raw == null) {
      return Optional.empty();
    }
    return Optional.of(convert(raw, type));
  }

  private String resolveRaw(String key) {
    if (key == null) {
      return null;
    }
    String envKey = toEnvKey(key);
    String envValue = envLookup.apply(envKey);
    if (envValue != null) {
      return envValue;
    }
    return System.getProperty(key);
  }

  private static String toEnvKey(String key) {
    return key.trim().replace('.', '_').replace('-', '_').toUpperCase();
  }

  private static <T> T convert(String raw, Class<T> type) {
    final Object converted;
    if (type == String.class) {
      converted = raw;
    } else if (type == Boolean.class) {
      converted = Boolean.parseBoolean(raw);
    } else if (type == Integer.class) {
      converted = Integer.parseInt(raw);
    } else if (type == Long.class) {
      converted = Long.parseLong(raw);
    } else if (type == Double.class) {
      converted = Double.parseDouble(raw);
    } else if (type == Duration.class) {
      converted = Duration.parse(raw);
    } else {
      throw new IllegalArgumentException("Unsupported config type: " + type.getName());
    }
    return type.cast(converted);
  }
}
