package io.javaoperatorsdk.operator.processing.retry;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import java.util.Optional;

public interface RetryExecution extends RetryInfo {

  /**
   * @return the time to wait until the next execution in milliseconds
   */
  Optional<Long> nextDelay();
}
