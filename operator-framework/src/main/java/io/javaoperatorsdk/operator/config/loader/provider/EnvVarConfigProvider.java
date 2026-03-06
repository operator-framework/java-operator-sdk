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
package io.javaoperatorsdk.operator.config.loader.provider;

import java.util.Optional;
import java.util.function.Function;

import io.javaoperatorsdk.operator.config.loader.ConfigProvider;

/**
 * A {@link ConfigProvider} that resolves configuration values from environment variables.
 *
 * <p>The key is converted to an environment variable name by replacing dots and hyphens with
 * underscores and converting to upper case (e.g. {@code josdk.cache-sync.timeout} → {@code
 * JOSDK_CACHE_SYNC_TIMEOUT}).
 *
 * <p>Supported value types are: {@link String}, {@link Boolean}, {@link Integer}, {@link Long},
 * {@link Double}, and {@link java.time.Duration} (ISO-8601 format, e.g. {@code PT30S}).
 */
public class EnvVarConfigProvider implements ConfigProvider {

  private final Function<String, String> envLookup;

  public EnvVarConfigProvider() {
    this(System::getenv);
  }

  EnvVarConfigProvider(Function<String, String> envLookup) {
    this.envLookup = envLookup;
  }

  @Override
  public <T> Optional<T> getValue(String key, Class<T> type) {
    if (key == null) {
      return Optional.empty();
    }
    String raw = envLookup.apply(toEnvKey(key));
    if (raw == null) {
      return Optional.empty();
    }
    return Optional.of(ConfigValueConverter.convert(raw, type));
  }

  static String toEnvKey(String key) {
    return key.trim().replace('.', '_').replace('-', '_').toUpperCase();
  }
}
