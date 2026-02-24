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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityListConfigProviderTest {

  @Test
  void returnsEmptyWhenAllProvidersReturnEmpty() {
    var provider =
        new PrirityListConfigProvider(
            List.of(
                new EnvVarConfigProvider(k -> null), new SystemPropertyConfigProvider(k -> null)));
    assertThat(provider.getValue("josdk.no.such.key", String.class)).isEmpty();
  }

  @Test
  void firstProviderWins() {
    var provider =
        new PrirityListConfigProvider(
            List.of(
                new EnvVarConfigProvider(k -> k.equals("JOSDK_TEST_KEY") ? "first" : null),
                new SystemPropertyConfigProvider(
                    k -> k.equals("josdk.test.key") ? "second" : null)));
    assertThat(provider.getValue("josdk.test.key", String.class)).hasValue("first");
  }

  @Test
  void fallsBackToLaterProviderWhenEarlierReturnsEmpty() {
    var provider =
        new PrirityListConfigProvider(
            List.of(
                new EnvVarConfigProvider(k -> null),
                new SystemPropertyConfigProvider(
                    k -> k.equals("josdk.test.key") ? "from-second" : null)));
    assertThat(provider.getValue("josdk.test.key", String.class)).hasValue("from-second");
  }

  @Test
  void respectsOrderWithThreeProviders() {
    var first = new EnvVarConfigProvider(k -> null);
    var second =
        new SystemPropertyConfigProvider(k -> k.equals("josdk.test.key") ? "from-second" : null);
    var third = new EnvVarConfigProvider(k -> k.equals("JOSDK_TEST_KEY") ? "from-third" : null);

    var provider = new PrirityListConfigProvider(List.of(first, second, third));
    assertThat(provider.getValue("josdk.test.key", String.class)).hasValue("from-second");
  }
}
