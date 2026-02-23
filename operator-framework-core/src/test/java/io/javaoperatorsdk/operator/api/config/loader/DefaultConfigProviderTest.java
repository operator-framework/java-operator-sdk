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
package io.javaoperatorsdk.operator.api.config.loader;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DefaultConfigProviderTest {

  private final DefaultConfigProvider provider = new DefaultConfigProvider();

  @Test
  void returnsEmptyWhenNeitherEnvNorPropertyIsSet() {
    assertThat(provider.getValue("josdk.no.such.key", String.class)).isEmpty();
  }

  // -- env variable tests -----------------------------------------------------

  @Test
  void readsStringFromEnvVariable() {
    var envProvider = new DefaultConfigProvider(k -> k.equals("JOSDK_TEST_STRING") ? "from-env" : null);
    assertThat(envProvider.getValue("josdk.test.string", String.class)).hasValue("from-env");
  }

  @Test
  void envVariableKeyUsesUppercaseWithUnderscores() {
    // dots and hyphens both become underscores, key is uppercased
    var envProvider = new DefaultConfigProvider(k -> k.equals("JOSDK_CACHE_SYNC_TIMEOUT") ? "PT10S" : null);
    assertThat(envProvider.getValue("josdk.cache-sync.timeout", Duration.class))
        .hasValue(Duration.ofSeconds(10));
  }

  @Test
  void envVariableTakesPrecedenceOverSystemProperty() {
    System.setProperty("josdk.test.precedence", "from-sysprop");
    try {
      var envProvider = new DefaultConfigProvider(k -> k.equals("JOSDK_TEST_PRECEDENCE") ? "from-env" : null);
      assertThat(envProvider.getValue("josdk.test.precedence", String.class)).hasValue("from-env");
    } finally {
      System.clearProperty("josdk.test.precedence");
    }
  }

  @Test
  void fallsBackToSystemPropertyWhenEnvVariableAbsent() {
    System.setProperty("josdk.test.fallback", "from-sysprop");
    try {
      var envProvider = new DefaultConfigProvider(k -> null);
      assertThat(envProvider.getValue("josdk.test.fallback", String.class)).hasValue("from-sysprop");
    } finally {
      System.clearProperty("josdk.test.fallback");
    }
  }

  @Test
  void readsStringFromSystemProperty() {
    System.setProperty("josdk.test.string", "hello");
    try {
      assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("hello");
    } finally {
      System.clearProperty("josdk.test.string");
    }
  }

  @Test
  void readsBooleanFromSystemProperty() {
    System.setProperty("josdk.test.bool", "true");
    try {
      assertThat(provider.getValue("josdk.test.bool", Boolean.class)).hasValue(true);
    } finally {
      System.clearProperty("josdk.test.bool");
    }
  }

  @Test
  void readsIntegerFromSystemProperty() {
    System.setProperty("josdk.test.integer", "42");
    try {
      assertThat(provider.getValue("josdk.test.integer", Integer.class)).hasValue(42);
    } finally {
      System.clearProperty("josdk.test.integer");
    }
  }

  @Test
  void readsLongFromSystemProperty() {
    System.setProperty("josdk.test.long", "123456789");
    try {
      assertThat(provider.getValue("josdk.test.long", Long.class)).hasValue(123456789L);
    } finally {
      System.clearProperty("josdk.test.long");
    }
  }

  @Test
  void readsDurationFromSystemProperty() {
    System.setProperty("josdk.test.duration", "PT30S");
    try {
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
      assertThatIllegalArgumentException()
          .isThrownBy(() -> provider.getValue("josdk.test.unsupported", Double.class))
          .withMessageContaining("Unsupported config type");
    } finally {
      System.clearProperty("josdk.test.unsupported");
    }
  }
}
