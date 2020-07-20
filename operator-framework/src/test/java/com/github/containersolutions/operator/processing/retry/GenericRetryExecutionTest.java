package com.github.containersolutions.operator.processing.retry;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.containersolutions.operator.processing.retry.GenericRetry.DEFAULT_INITIAL_INTERVAL;
import static com.github.containersolutions.operator.processing.retry.GenericRetry.DEFAULT_MULTIPLIER;
import static org.assertj.core.api.Assertions.assertThat;

public class GenericRetryExecutionTest {

    @Test
    public void forFirstBackOffAlwaysReturnsZero() {
        assertThat(getDefaultRetryExecution().nextDelay().get()).isEqualTo(0);
    }

    @Test
    public void delayIsMultipliedEveryNextDelayCall() {
        RetryExecution retryExecution = getDefaultRetryExecution();

        Optional<Long> res = callNextDelayNTimes(retryExecution, 2);
        assertThat(res.get()).isEqualTo(DEFAULT_INITIAL_INTERVAL);

        res = retryExecution.nextDelay();
        assertThat(res.get()).isEqualTo((long) (DEFAULT_INITIAL_INTERVAL * DEFAULT_MULTIPLIER));

        res = retryExecution.nextDelay();
        assertThat(res.get()).isEqualTo((long) (DEFAULT_INITIAL_INTERVAL * DEFAULT_MULTIPLIER * DEFAULT_MULTIPLIER));
    }

    @Test
    public void noNextDelayIfMaxAttemptLimitReached() {
        RetryExecution retryExecution = GenericRetry.defaultLimitedExponentialRetry().setMaxAttempts(3).initExecution();
        Optional<Long> res = callNextDelayNTimes(retryExecution, 3);
        assertThat(res).isNotEmpty();

        res = retryExecution.nextDelay();
        assertThat(res).isEmpty();
    }

    @Test
    public void canLimitMaxIntervalLength() {
        RetryExecution retryExecution = GenericRetry.defaultLimitedExponentialRetry()
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
        assertThat(retryExecution.nextDelay().get()).isZero();
        assertThat(retryExecution.nextDelay()).isEmpty();
    }

    @Test
    public void supportsIsLastExecution() {
        GenericRetryExecution execution = new GenericRetry().setMaxAttempts(2).initExecution();
        assertThat(execution.isLastExecution()).isFalse();

        execution.nextDelay();
        execution.nextDelay();
        assertThat(execution.isLastExecution()).isTrue();
    }

    private RetryExecution getDefaultRetryExecution() {
        return GenericRetry.defaultLimitedExponentialRetry().initExecution();
    }

    public Optional<Long> callNextDelayNTimes(RetryExecution retryExecution, int n) {
        for (int i = 0; i < n - 1; i++) {
            retryExecution.nextDelay();
        }
        return retryExecution.nextDelay();
    }

}
