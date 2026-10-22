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
package io.javaoperatorsdk.operator.processing.retry;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericRetryTest {

  @Test
  void builderUsesDefaults() {
    var retry = GenericRetry.builder().build();

    assertThat(retry.getMaxAttempts()).isEqualTo(GradualRetry.DEFAULT_MAX_ATTEMPTS);
    assertThat(retry.getInitialInterval()).isEqualTo(GradualRetry.DEFAULT_INITIAL_INTERVAL);
    assertThat(retry.getIntervalMultiplier()).isEqualTo(GradualRetry.DEFAULT_MULTIPLIER);
    assertThat(retry.getMaxInterval()).isEqualTo(GradualRetry.DEFAULT_MAX_INTERVAL);
  }

  @Test
  void builderSetsAllValues() {
    var retry =
        GenericRetry.builder()
            .withMaxAttempts(7)
            .withInitialInterval(Duration.ofSeconds(3))
            .withIntervalMultiplier(2.5)
            .withMaxInterval(Duration.ofMinutes(1))
            .build();

    assertThat(retry.getMaxAttempts()).isEqualTo(7);
    assertThat(retry.getInitialInterval()).isEqualTo(3000);
    assertThat(retry.getIntervalMultiplier()).isEqualTo(2.5);
    assertThat(retry.getMaxInterval()).isEqualTo(60_000);
  }

  @Test
  void builderSupportsUnlimitedAndLinearRetry() {
    var retry =
        GenericRetry.builder()
            .withIntervalMultiplier(3)
            .withoutMaxAttempts()
            .withoutMaxInterval()
            .withLinearRetry()
            .build();

    assertThat(retry.getMaxAttempts()).isEqualTo(-1);
    assertThat(retry.getMaxInterval()).isEqualTo(-1);
    assertThat(retry.getIntervalMultiplier()).isEqualTo(1);
  }

  @Test
  void builderCreatesIndependentInstances() {
    var builder = GenericRetry.builder().withMaxAttempts(3);
    var first = builder.build();
    var second = builder.withMaxAttempts(10).build();

    assertThat(first.getMaxAttempts()).isEqualTo(3);
    assertThat(second.getMaxAttempts()).isEqualTo(10);
  }

  @Test
  void toBuilderCopiesValuesWithoutModifyingOriginal() {
    var original =
        GenericRetry.builder()
            .withMaxAttempts(4)
            .withInitialInterval(100)
            .withMaxInterval(500)
            .build();

    var derived = original.toBuilder().withMaxAttempts(8).build();

    assertThat(original.getMaxAttempts()).isEqualTo(4);
    assertThat(derived.getMaxAttempts()).isEqualTo(8);
    assertThat(derived.getInitialInterval()).isEqualTo(100);
    assertThat(derived.getMaxInterval()).isEqualTo(500);
    assertThat(derived.getIntervalMultiplier()).isEqualTo(original.getIntervalMultiplier());
  }

  @Test
  void factoryMethods() {
    assertThat(GenericRetry.noRetry().getMaxAttempts()).isZero();

    var every10s = GenericRetry.every10second10TimesRetry();
    assertThat(every10s.getMaxAttempts()).isEqualTo(10);
    assertThat(every10s.getInitialInterval()).isEqualTo(10_000);
    assertThat(every10s.getIntervalMultiplier()).isEqualTo(1);
  }
}
