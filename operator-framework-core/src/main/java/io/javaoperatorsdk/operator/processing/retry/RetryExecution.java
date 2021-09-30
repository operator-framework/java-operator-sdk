package io.javaoperatorsdk.operator.processing.retry;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.RetryInfo;

public interface RetryExecution extends RetryInfo {

  /**
   * Calculates the delay for the next execution. This method should return 0, when called first
   * time;
   *
   * @return the time to wait until the next execution in milliseconds
   */
  Optional<Long> nextDelay();
}
