package io.javaoperatorsdk.operator.processing.retry;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

public interface RetryExecution extends RetryInfo {

  /**
   * @return the time to wait until the next execution in milliseconds
   */
  Optional<Long> nextDelay();
}
