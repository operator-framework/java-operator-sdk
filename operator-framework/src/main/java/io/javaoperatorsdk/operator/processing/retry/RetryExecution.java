package io.javaoperatorsdk.operator.processing.retry;

import io.javaoperatorsdk.operator.api.RetryInfo;
import java.util.Optional;

public interface RetryExecution extends RetryInfo {

  /**
   * Calculates the delay for the next execution. This method should return 0, when called first
   * time;
   *
   * @return
   */
  Optional<Long> nextDelay();
}
