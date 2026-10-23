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
package io.javaoperatorsdk.operator.performance.results;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Single place configuring how result files are read and written. */
final class Json {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          // keeps diffs on the results branch stable
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private Json() {}

  static void write(Path file, Object value) throws IOException {
    var directory = file.getParent();
    if (directory != null) {
      Files.createDirectories(directory);
    }
    Files.writeString(
        file, MAPPER.writeValueAsString(value) + System.lineSeparator(), StandardCharsets.UTF_8);
  }

  static <T> T read(Path file, Class<T> type) throws IOException {
    return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
  }
}
