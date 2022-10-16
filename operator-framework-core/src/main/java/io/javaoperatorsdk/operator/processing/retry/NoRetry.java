package io.javaoperatorsdk.operator.processing.retry;

public class NoRetry implements Retry {

  /* should not be instanciated */
  private NoRetry() {}

  @Override
  public RetryExecution initExecution() {
    return null;
  }
}
