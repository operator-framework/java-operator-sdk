package io.javaoperatorsdk.operator.api.reconciler;

public interface RetryInfo {

  int getAttemptCount();

  boolean isLastAttempt();
}
