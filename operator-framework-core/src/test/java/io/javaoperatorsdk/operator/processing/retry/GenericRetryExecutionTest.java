package io.javaoperatorsdk.operator.processing.retry;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericRetryExecutionTest {

  @Test
  public void forFirstBackOffAlwaysReturnsInitialInterval() {
    assertThat(getDefaultRetryExecution().nextDelay().get())
        .isEqualTo(RetryConfiguration.DEFAULT_INITIAL_INTERVAL);
  }

  @Test
  public void delayIsMultipliedEveryNextDelayCall() {
    RetryExecution retryExecution = getDefaultRetryExecution();

    Optional<Long> res = callNextDelayNTimes(retryExecution, 1);
    assertThat(res.get()).isEqualTo(RetryConfiguration.DEFAULT_INITIAL_INTERVAL);

    res = retryExecution.nextDelay();
    assertThat(res.get())
        .isEqualTo((long) (RetryConfiguration.DEFAULT_INITIAL_INTERVAL
            * RetryConfiguration.DEFAULT_MULTIPLIER));

    res = retryExecution.nextDelay();
    assertThat(res.get())
        .isEqualTo(
            (long) (RetryConfiguration.DEFAULT_INITIAL_INTERVAL
                * RetryConfiguration.DEFAULT_MULTIPLIER
                * RetryConfiguration.DEFAULT_MULTIPLIER));
  }

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
