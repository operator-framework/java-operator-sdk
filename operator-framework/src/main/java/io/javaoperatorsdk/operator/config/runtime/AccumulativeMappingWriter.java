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
package io.javaoperatorsdk.operator.config.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;

/**
 * The writer is able to load an existing resource file as a Map and override it with the new
 * mappings added to the existing mappings. Every entry corresponds to a line in the resource file
 * where key and values are separated by comma.
 */
class AccumulativeMappingWriter {

  private final Map<String, String> mappings = new ConcurrentHashMap<>();
  private final String resourcePath;
  private final ProcessingEnvironment processingEnvironment;

  public AccumulativeMappingWriter(
      String resourcePath, ProcessingEnvironment processingEnvironment) {
    this.resourcePath = resourcePath;
    this.processingEnvironment = processingEnvironment;
  }

  public AccumulativeMappingWriter loadExistingMappings() {
    try {
      final var readonlyResource =
          processingEnvironment
              .getFiler()
              .getResource(StandardLocation.CLASS_OUTPUT, "", resourcePath);

      try (BufferedReader bufferedReader =
          new BufferedReader(new InputStreamReader(readonlyResource.openInputStream()))) {
        final var existingLines =
            bufferedReader
                .lines()
                .map(l -> l.split(","))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        mappings.putAll(existingLines);
      }
    } catch (IOException e) {
    }
    return this;
  }

  /** Add a new mapping */
  public AccumulativeMappingWriter add(String key, String value) {
    this.mappings.put(key, value);
    return this;
  }

  /**
   * Generates or override the resource file with the given path ({@link
   * AccumulativeMappingWriter#resourcePath})
   */
  public void flush() {
    PrintWriter printWriter = null;
    try {
      final var resource =
          processingEnvironment
              .getFiler()
              .createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath);
      printWriter = new PrintWriter(resource.openOutputStream());

      for (Map.Entry<String, String> entry : mappings.entrySet()) {
        printWriter.println(entry.getKey() + "," + entry.getValue());
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      if (printWriter != null) {
        printWriter.close();
      }
    }
  }
}
