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

public class GenericRetryExecutionTest {

  @Test
  public void noNextDelayIfMaxAttemptLimitReached() {
    RetryExecution retryExecution =
        GenericRetry.defaultLimitedExponentialRetry().setMaxAttempts(3).initExecution();
    Optional<Long> res = callNextDelayNTimes(retryExecution, 2);
    assertThat(res).isNotEmpty();

    res = retryExecution.nextDelay();
    assertThat(res).isEmpty();
  }

  @Test
  public void canLimitMaxIntervalLength() {
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
  public void supportsNoRetry() {
    RetryExecution retryExecution = GenericRetry.noRetry().initExecution();
    assertThat(retryExecution.nextDelay()).isEmpty();
  }

  @Test
  public void supportsIsLastExecution() {
    GenericRetryExecution execution = new GenericRetry().setMaxAttempts(2).initExecution();
    assertThat(execution.isLastAttempt()).isFalse();

    execution.nextDelay();
    execution.nextDelay();
    assertThat(execution.isLastAttempt()).isTrue();
  }

  @Test
  public void returnAttemptIndex() {
    RetryExecution retryExecution = GenericRetry.defaultLimitedExponentialRetry().initExecution();

    assertThat(retryExecution.getAttemptCount()).isEqualTo(0);
    retryExecution.nextDelay();
    assertThat(retryExecution.getAttemptCount()).isEqualTo(1);
  }

  private RetryExecution getDefaultRetryExecution() {
    return GenericRetry.defaultLimitedExponentialRetry().initExecution();
  }

  public Optional<Long> callNextDelayNTimes(RetryExecution retryExecution, int n) {
    for (int i = 0; i < n; i++) {
      retryExecution.nextDelay();
    }
    return retryExecution.nextDelay();
  }
}
