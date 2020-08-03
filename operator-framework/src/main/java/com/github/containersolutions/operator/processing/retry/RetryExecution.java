package com.github.containersolutions.operator.processing.retry;

import java.util.Optional;

public interface RetryExecution {

    /**
     * Calculates the delay for the next execution. This method should return 0, when called first time;
     *
     * @return
     */
    Optional<Long> nextDelay();

    /**
     * @return true, if the last returned delay is, the last returned values, thus there will be no further retry
     */
    boolean isLastExecution();
}
