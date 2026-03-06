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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class YamlConfigProviderTest {

  // -- Map constructor --------------------------------------------------------

  @Test
  void returnsEmptyWhenKeyAbsent() {
    var provider = new YamlConfigProvider(Map.of());
    assertThat(provider.getValue("josdk.no.such.key", String.class)).isEmpty();
  }

  @Test
  void returnsEmptyForNullKey() {
    var provider = new YamlConfigProvider(Map.of("josdk", Map.of("test", "value")));
    assertThat(provider.getValue(null, String.class)).isEmpty();
  }

  @Test
  void readsTopLevelString() {
    var provider = new YamlConfigProvider(Map.of("key", "hello"));
    assertThat(provider.getValue("key", String.class)).hasValue("hello");
  }

  @Test
  void readsNestedString() {
    var provider =
        new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("string", "hello"))));
    assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("hello");
  }

  @Test
  void readsBoolean() {
    var provider = new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("bool", "true"))));
    assertThat(provider.getValue("josdk.test.bool", Boolean.class)).hasValue(true);
  }

  @Test
  void readsInteger() {
    var provider = new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("integer", 42))));
    assertThat(provider.getValue("josdk.test.integer", Integer.class)).hasValue(42);
  }

  @Test
  void readsLong() {
    var provider =
        new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("long", 123456789L))));
    assertThat(provider.getValue("josdk.test.long", Long.class)).hasValue(123456789L);
  }

  @Test
  void readsDouble() {
    var provider =
        new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("double", "3.14"))));
    assertThat(provider.getValue("josdk.test.double", Double.class)).hasValue(3.14);
  }

  @Test
  void readsDuration() {
    var provider =
        new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("duration", "PT30S"))));
    assertThat(provider.getValue("josdk.test.duration", Duration.class))
        .hasValue(Duration.ofSeconds(30));
  }

  @Test
  void returnsEmptyWhenIntermediateSegmentMissing() {
    var provider = new YamlConfigProvider(Map.of("josdk", Map.of("other", "value")));
    assertThat(provider.getValue("josdk.test.key", String.class)).isEmpty();
  }

  @Test
  void returnsEmptyWhenIntermediateSegmentIsLeaf() {
    // "josdk.test" is a leaf – trying to drill further should return empty
    var provider = new YamlConfigProvider(Map.of("josdk", Map.of("test", "leaf")));
    assertThat(provider.getValue("josdk.test.key", String.class)).isEmpty();
  }

  @Test
  void throwsForUnsupportedType() {
    var provider =
        new YamlConfigProvider(Map.of("josdk", Map.of("test", Map.of("unsupported", "value"))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> provider.getValue("josdk.test.unsupported", AtomicInteger.class))
        .withMessageContaining("Unsupported config type");
  }

  // -- Path constructor -------------------------------------------------------

  @Test
  void loadsFromFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("test.yaml");
    Files.writeString(
        file,
        """
        josdk:
          test:
            string: from-file
            integer: 7
        """);

    var provider = new YamlConfigProvider(file);
    assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("from-file");
    assertThat(provider.getValue("josdk.test.integer", Integer.class)).hasValue(7);
  }

  @Test
  void throwsUncheckedIOExceptionForMissingFile(@TempDir Path dir) {
    Path missing = dir.resolve("does-not-exist.yaml");
    assertThatExceptionOfType(UncheckedIOException.class)
        .isThrownBy(() -> new YamlConfigProvider(missing))
        .withMessageContaining("does-not-exist.yaml");
  }
}
