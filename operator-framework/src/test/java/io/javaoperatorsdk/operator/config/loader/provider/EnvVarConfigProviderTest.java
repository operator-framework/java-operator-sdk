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

class EnvVarConfigProviderTest {

  @Test
  void returnsEmptyWhenEnvVariableAbsent() {
    var provider = new EnvVarConfigProvider(k -> null);
    assertThat(provider.getValue("josdk.no.such.key", String.class)).isEmpty();
  }

  @Test
  void returnsEmptyForNullKey() {
    var provider = new EnvVarConfigProvider(k -> "value");
    assertThat(provider.getValue(null, String.class)).isEmpty();
  }

  @Test
  void readsStringFromEnvVariable() {
    var provider = new EnvVarConfigProvider(k -> k.equals("JOSDK_TEST_STRING") ? "from-env" : null);
    assertThat(provider.getValue("josdk.test.string", String.class)).hasValue("from-env");
  }

  @Test
  void convertsDotsAndHyphensToUnderscoresAndUppercases() {
    var provider =
        new EnvVarConfigProvider(k -> k.equals("JOSDK_CACHE_SYNC_TIMEOUT") ? "PT10S" : null);
    assertThat(provider.getValue("josdk.cache-sync.timeout", Duration.class))
        .hasValue(Duration.ofSeconds(10));
  }

  @Test
  void throwsForUnsupportedType() {
    var provider =
        new EnvVarConfigProvider(k -> k.equals("JOSDK_TEST_UNSUPPORTED") ? "value" : null);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> provider.getValue("josdk.test.unsupported", AtomicInteger.class))
        .withMessageContaining("Unsupported config type");
  }
}
