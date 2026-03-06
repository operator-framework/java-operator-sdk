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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PropertiesConfigProviderTest {

  // -- Properties constructor -------------------------------------------------

  @Test
  void returnsEmptyWhenKeyAbsent() {
    var provider = new PropertiesConfigProvider(new Properties());
    assertThat(provider.getValue("josdk.no.such.key", String.class)).isEmpty();
  }

  @Test
  void returnsEmptyForNullKey() {
    var props = new Properties();
    props.setProperty("josdk.test.key", "value");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue(null, String.class)).isEmpty();
  }

  @Test
  void readsString() {
    var props = new Properties();
    props.setProperty("josdk.test.string", "hello");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("hello");
  }

  @Test
  void readsBoolean() {
    var props = new Properties();
    props.setProperty("josdk.test.bool", "true");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue("josdk.test.bool", Boolean.class)).hasValue(true);
  }

  @Test
  void readsInteger() {
    var props = new Properties();
    props.setProperty("josdk.test.integer", "42");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue("josdk.test.integer", Integer.class)).hasValue(42);
  }

  @Test
  void readsLong() {
    var props = new Properties();
    props.setProperty("josdk.test.long", "123456789");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue("josdk.test.long", Long.class)).hasValue(123456789L);
  }

  @Test
  void readsDouble() {
    var props = new Properties();
    props.setProperty("josdk.test.double", "3.14");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue("josdk.test.double", Double.class)).hasValue(3.14);
  }

  @Test
  void readsDuration() {
    var props = new Properties();
    props.setProperty("josdk.test.duration", "PT30S");
    var provider = new PropertiesConfigProvider(props);
    assertThat(provider.getValue("josdk.test.duration", Duration.class))
        .hasValue(Duration.ofSeconds(30));
  }

  @Test
  void throwsForUnsupportedType() {
    var props = new Properties();
    props.setProperty("josdk.test.unsupported", "value");
    var provider = new PropertiesConfigProvider(props);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> provider.getValue("josdk.test.unsupported", AtomicInteger.class))
        .withMessageContaining("Unsupported config type");
  }

  // -- Path constructor -------------------------------------------------------

  @Test
  void loadsFromFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("test.properties");
    Files.writeString(file, "josdk.test.string=from-file\njosdk.test.integer=7\n");

    var provider = new PropertiesConfigProvider(file);
    assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("from-file");
    assertThat(provider.getValue("josdk.test.integer", Integer.class)).hasValue(7);
  }

  @Test
  void throwsUncheckedIOExceptionForMissingFile(@TempDir Path dir) {
    Path missing = dir.resolve("does-not-exist.properties");
    assertThatExceptionOfType(UncheckedIOException.class)
        .isThrownBy(() -> new PropertiesConfigProvider(missing))
        .withMessageContaining("does-not-exist.properties");
  }
}
