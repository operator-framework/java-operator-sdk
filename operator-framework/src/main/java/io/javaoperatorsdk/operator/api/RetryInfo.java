package io.javaoperatorsdk.operator.api;

public interface RetryInfo {

  int getAttemptCount();

  boolean isLastAttempt();
}
