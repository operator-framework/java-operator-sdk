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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SystemPropertyConfigProviderTest {

  @Test
  void returnsEmptyWhenPropertyAbsent() {
    var provider = new SystemPropertyConfigProvider(k -> null);
    assertThat(provider.getValue("josdk.no.such.key", String.class)).isEmpty();
  }

  @Test
  void returnsEmptyForNullKey() {
    var provider = new SystemPropertyConfigProvider(k -> "value");
    assertThat(provider.getValue(null, String.class)).isEmpty();
  }

  @Test
  void readsStringFromSystemProperty() {
    System.setProperty("josdk.test.string", "hello");
    try {
      var provider = new SystemPropertyConfigProvider();
      assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("hello");
    } finally {
      System.clearProperty("josdk.test.string");
    }
  }

  @Test
  void readsBooleanFromSystemProperty() {
    System.setProperty("josdk.test.bool", "true");
    try {
      var provider = new SystemPropertyConfigProvider();
      assertThat(provider.getValue("josdk.test.bool", Boolean.class)).hasValue(true);
    } finally {
      System.clearProperty("josdk.test.bool");
    }
  }

  @Test
  void readsIntegerFromSystemProperty() {
    System.setProperty("josdk.test.integer", "42");
    try {
      var provider = new SystemPropertyConfigProvider();
      assertThat(provider.getValue("josdk.test.integer", Integer.class)).hasValue(42);
    } finally {
      System.clearProperty("josdk.test.integer");
    }
  }

  @Test
  void readsLongFromSystemProperty() {
    System.setProperty("josdk.test.long", "123456789");
    try {
      var provider = new SystemPropertyConfigProvider();
      assertThat(provider.getValue("josdk.test.long", Long.class)).hasValue(123456789L);
    } finally {
      System.clearProperty("josdk.test.long");
    }
  }

  @Test
  void readsDurationFromSystemProperty() {
    System.setProperty("josdk.test.duration", "PT30S");
    try {
      var provider = new SystemPropertyConfigProvider();
      assertThat(provider.getValue("josdk.test.duration", Duration.class))
          .hasValue(Duration.ofSeconds(30));
    } finally {
      System.clearProperty("josdk.test.duration");
    }
  }

  @Test
  void throwsForUnsupportedType() {
    System.setProperty("josdk.test.unsupported", "value");
    try {
      var provider = new SystemPropertyConfigProvider();
      assertThatIllegalArgumentException()
          .isThrownBy(() -> provider.getValue("josdk.test.unsupported", AtomicInteger.class))
          .withMessageContaining("Unsupported config type");
    } finally {
      System.clearProperty("josdk.test.unsupported");
    }
  }
}
