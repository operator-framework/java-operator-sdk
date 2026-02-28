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
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.config.loader.ConfigProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * A {@link ConfigProvider} that resolves configuration values from a YAML file.
 *
 * <p>Keys use dot-separated notation to address nested YAML mappings (e.g. {@code
 * josdk.cache-sync.timeout} maps to {@code josdk → cache-sync → timeout} in the YAML document).
 * Leaf values are converted to the requested type via {@link ConfigValueConverter}. Supported value
 * types are: {@link String}, {@link Boolean}, {@link Integer}, {@link Long}, {@link Double}, and
 * {@link java.time.Duration} (ISO-8601 format, e.g. {@code PT30S}).
 */
public class YamlConfigProvider implements ConfigProvider {

  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

  private final Map<String, Object> data;

  /**
   * Loads YAML from the given file path.
   *
   * @throws UncheckedIOException if the file cannot be read
   */
  public YamlConfigProvider(Path path) {
    this.data = load(path);
  }

  /** Uses the supplied map directly (useful for testing). */
  public YamlConfigProvider(Map<String, Object> data) {
    this.data = data;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Optional<T> getValue(String key, Class<T> type) {
    if (key == null) {
      return Optional.empty();
    }
    String[] parts = key.split("\\.", -1);
    Object current = data;
    for (String part : parts) {
      if (!(current instanceof Map)) {
        return Optional.empty();
      }
      current = ((Map<String, Object>) current).get(part);
      if (current == null) {
        return Optional.empty();
      }
    }
    return Optional.of(ConfigValueConverter.convert(current.toString(), type));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> load(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      Map<String, Object> result = MAPPER.readValue(in, Map.class);
      return result != null ? result : Map.of();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load config YAML from " + path, e);
    }
  }
}
