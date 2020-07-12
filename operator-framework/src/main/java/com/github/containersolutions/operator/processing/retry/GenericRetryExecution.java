package com.github.containersolutions.operator.processing.retry;

import java.util.Optional;


public class GenericRetryExecution implements RetryExecution {

    private final GenericRetry genericRetry;

    private int lastAttemptIndex = 0;
    private long currentInterval;

    public GenericRetryExecution(GenericRetry genericRetry) {
        this.genericRetry = genericRetry;
        this.currentInterval = genericRetry.getInitialInterval();
    }

    /**
     * Note that first attempt is always 0. Since this implementation is tailored for event scheduling.
     */
    public Optional<Long> nextDelay() {
        if (lastAttemptIndex == 0) {
            lastAttemptIndex++;
            return Optional.of(0L);
        }
        if (genericRetry.getMaxAttempts() > -1 && lastAttemptIndex >= genericRetry.getMaxAttempts()) {
            return Optional.empty();
        }
        if (lastAttemptIndex > 1) {
            currentInterval = (long) (currentInterval * genericRetry.getIntervalMultiplier());
            if (genericRetry.getMaxInterval() > -1 && currentInterval > genericRetry.getMaxInterval()) {
                currentInterval = genericRetry.getMaxInterval();
            }
        }
        lastAttemptIndex++;
        return Optional.of(currentInterval);
    }

    @Override
    public boolean isLastExecution() {
        return genericRetry.getMaxAttempts() > -1 && lastAttemptIndex >= genericRetry.getMaxAttempts();
    }
}
