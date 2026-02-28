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

import java.time.Duration;

/** Utility for converting raw string config values to typed instances. */
final class ConfigValueConverter {

  private ConfigValueConverter() {}

  /**
   * Converts {@code raw} to an instance of {@code type}. Supported types: {@link String}, {@link
   * Boolean}, {@link Integer}, {@link Long}, {@link Double}, and {@link Duration} (ISO-8601 format,
   * e.g. {@code PT30S}).
   *
   * @throws IllegalArgumentException if {@code type} is not supported
   */
  public static <T> T convert(String raw, Class<T> type) {
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
