package io.javaoperatorsdk.operator.api.reconciler;

public interface RetryInfo {
  /**
   * @return current retry attempt count. 0 if the current execution is not a retry.
   */
  int getAttemptCount();

  /**
   * @return true, if the current attempt is the last one in regard to the retry limit
   *     configuration.
   */
  boolean isLastAttempt();
}
