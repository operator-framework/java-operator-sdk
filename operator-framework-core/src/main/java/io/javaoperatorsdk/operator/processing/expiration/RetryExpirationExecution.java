package io.javaoperatorsdk.operator.processing.expiration;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

public class RetryExpirationExecution implements ExpirationExecution {

  public static final long NO_MORE_EXPIRATION = -1L;

  private LocalDateTime lastRefreshTime;
  private long delayUntilExpiration;
  private final RetryExecution retryExecution;

  public RetryExpirationExecution(RetryExecution retryExecution) {
    this.retryExecution = retryExecution;
  }

  @Override
  public boolean isExpired() {
    if (lastRefreshTime == null) {
      return true;
    }
    if (Objects.equals(delayUntilExpiration, NO_MORE_EXPIRATION)) {
      return false;
    } else {
      return LocalDateTime.now()
          .isAfter(lastRefreshTime.plus(delayUntilExpiration, ChronoUnit.MILLIS));
    }
  }

  @Override
  public void refreshed() {
    lastRefreshTime = LocalDateTime.now();
    delayUntilExpiration = retryExecution.nextDelay().orElse(NO_MORE_EXPIRATION);
  }
}
