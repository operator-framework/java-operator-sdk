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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericRetryExecutionTest {

  @Test
  void noNextDelayIfMaxAttemptLimitReached() {
    RetryExecution retryExecution =
        GenericRetry.defaultLimitedExponentialRetry().setMaxAttempts(3).initExecution();
    Optional<Long> res = callNextDelayNTimes(retryExecution, 2);
    assertThat(res).isNotEmpty();

    res = retryExecution.nextDelay();
    assertThat(res).isEmpty();
  }

  @Test
  void canLimitMaxIntervalLength() {
    RetryExecution retryExecution =
        GenericRetry.defaultLimitedExponentialRetry()
            .setInitialInterval(2000)
            .setMaxInterval(4500)
            .setIntervalMultiplier(2)
            .initExecution();

    Optional<Long> res = callNextDelayNTimes(retryExecution, 4);

    assertThat(res.get()).isEqualTo(4500);
  }

  @Test
  void supportsNoRetry() {
    RetryExecution retryExecution = GenericRetry.noRetry().initExecution();
    assertThat(retryExecution.nextDelay()).isEmpty();
  }

  @Test
  void supportsIsLastExecution() {
    GenericRetryExecution execution = new GenericRetry().setMaxAttempts(2).initExecution();
    assertThat(execution.isLastAttempt()).isFalse();

    execution.nextDelay();
    execution.nextDelay();
    assertThat(execution.isLastAttempt()).isTrue();
  }

  @Test
  void returnAttemptIndex() {
    RetryExecution retryExecution = GenericRetry.defaultLimitedExponentialRetry().initExecution();

    assertThat(retryExecution.getAttemptCount()).isEqualTo(0);
    retryExecution.nextDelay();
    assertThat(retryExecution.getAttemptCount()).isEqualTo(1);
  }

  @Test
  void remainingDurationEmptyBeforeFirstNextDelay() {
    RetryExecution retryExecution = GenericRetry.defaultLimitedExponentialRetry().initExecution();

    assertThat(retryExecution.remainingDurationUntilNextRetry()).isEmpty();
  }

  @Test
  void remainingDurationPresentAfterNextDelay() {
    long interval = 60_000L;
    RetryExecution retryExecution = new GenericRetry().setInitialInterval(interval).initExecution();

    retryExecution.nextDelay();

    Optional<java.time.Duration> remaining = retryExecution.remainingDurationUntilNextRetry();
    assertThat(remaining).isPresent();
    assertThat(remaining.get().toMillis()).isPositive().isLessThanOrEqualTo(interval);
  }

  @Test
  void remainingDurationEmptyAfterIntervalElapsed() throws InterruptedException {
    RetryExecution retryExecution = new GenericRetry().setInitialInterval(20).initExecution();

    retryExecution.nextDelay();
    Thread.sleep(60);

    assertThat(retryExecution.remainingDurationUntilNextRetry()).isEmpty();
  }

  @Test
  void remainingDurationReflectsUpdatedIntervalAfterSubsequentNextDelay() {
    long initialInterval = 1000L;
    double multiplier = 2.0;
    RetryExecution retryExecution =
        new GenericRetry()
            .setInitialInterval(initialInterval)
            .setIntervalMultiplier(multiplier)
            .initExecution();

    // first two calls keep the initial interval (multiplier only kicks in after attempt 1)
    retryExecution.nextDelay();
    retryExecution.nextDelay();
    // third call doubles the interval to 2000ms
    retryExecution.nextDelay();

    Optional<java.time.Duration> remaining = retryExecution.remainingDurationUntilNextRetry();
    assertThat(remaining).isPresent();
    assertThat(remaining.get().toMillis())
        .isPositive()
        .isLessThanOrEqualTo((long) (initialInterval * multiplier));
  }

  Optional<Long> callNextDelayNTimes(RetryExecution retryExecution, int n) {
    for (int i = 0; i < n; i++) {
      retryExecution.nextDelay();
    }
    return retryExecution.nextDelay();
  }
}
