package io.javaoperatorsdk.operator.processing.retry;

import java.util.Optional;

public class GenericRetryExecution implements RetryExecution {

  private final GenericRetry genericRetry;

  private int lastAttemptIndex = 0;
  private long currentInterval;

  public GenericRetryExecution(GenericRetry genericRetry) {
    this.genericRetry = genericRetry;
    this.currentInterval = genericRetry.getInitialInterval();
  }

  public Optional<Long> nextDelay() {
    if (genericRetry.getMaxAttempts() > -1 && lastAttemptIndex >= genericRetry.getMaxAttempts()) {
      return Optional.empty();
    }
    if (lastAttemptIndex > 1) {
      currentInterval = (long) (currentInterval * genericRetry.getIntervalMultiplier());
      if (genericRetry.getMaxInterval() > -1 && currentInterval > genericRetry.getMaxInterval()) {
        currentInterval = genericRetry.getMaxInterval();
      }
    }
    lastAttemptIndex++;
    return Optional.of(currentInterval);
  }

  @Override
  public boolean isLastAttempt() {
    return genericRetry.getMaxAttempts() > -1 && lastAttemptIndex >= genericRetry.getMaxAttempts();
  }

  @Override
  public int getAttemptCount() {
    return lastAttemptIndex;
  }
}
