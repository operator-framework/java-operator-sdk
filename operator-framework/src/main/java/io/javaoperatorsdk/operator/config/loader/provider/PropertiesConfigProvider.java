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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import io.javaoperatorsdk.operator.config.loader.ConfigProvider;

/**
 * A {@link ConfigProvider} that resolves configuration values from a {@link Properties} file.
 *
 * <p>Keys are looked up as-is against the loaded properties. Supported value types are: {@link
 * String}, {@link Boolean}, {@link Integer}, {@link Long}, {@link Double}, and {@link
 * java.time.Duration} (ISO-8601 format, e.g. {@code PT30S}).
 */
public class PropertiesConfigProvider implements ConfigProvider {

  private final Properties properties;

  /**
   * Loads properties from the given file path.
   *
   * @throws UncheckedIOException if the file cannot be read
   */
  public PropertiesConfigProvider(Path path) {
    this.properties = load(path);
  }

  /** Uses the supplied {@link Properties} instance directly. */
  public PropertiesConfigProvider(Properties properties) {
    this.properties = properties;
  }

  @Override
  public <T> Optional<T> getValue(String key, Class<T> type) {
    if (key == null) {
      return Optional.empty();
    }
    String raw = properties.getProperty(key);
    if (raw == null) {
      return Optional.empty();
    }
    return Optional.of(ConfigValueConverter.convert(raw, type));
  }

  private static Properties load(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      Properties props = new Properties();
      props.load(in);
      return props;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load config properties from " + path, e);
    }
  }
}
