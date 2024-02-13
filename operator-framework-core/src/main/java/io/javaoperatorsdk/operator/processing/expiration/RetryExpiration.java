package io.javaoperatorsdk.operator.processing.expiration;

import io.javaoperatorsdk.operator.processing.retry.Retry;

public class RetryExpiration implements Expiration {

  private final Retry retry;

  public RetryExpiration(Retry retry) {
    this.retry = retry;
  }

  @Override
  public ExpirationExecution initExecution() {
    return new RetryExpirationExecution(retry.initExecution());
  }
}
