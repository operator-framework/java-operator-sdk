package io.javaoperatorsdk.operator.processing.retry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class GenericRetryExecution implements RetryExecution {

  private final GenericRetry genericRetry;

  private AtomicInteger lastAttemptIndex = new AtomicInteger(0);
  private volatile long currentInterval;

  public GenericRetryExecution(GenericRetry genericRetry) {
    this.genericRetry = genericRetry;
    this.currentInterval = genericRetry.getInitialInterval();
  }

  public Optional<Long> nextDelay() {
    if (genericRetry.getMaxAttempts() > -1
        && lastAttemptIndex.get() >= genericRetry.getMaxAttempts()) {
      return Optional.empty();
    }
    if (lastAttemptIndex.get() > 1) {
      currentInterval = (long) (currentInterval * genericRetry.getIntervalMultiplier());
      if (genericRetry.getMaxInterval() > -1 && currentInterval > genericRetry.getMaxInterval()) {
        currentInterval = genericRetry.getMaxInterval();
      }
    }
    lastAttemptIndex.incrementAndGet();
    return Optional.of(currentInterval);
  }

  @Override
  public boolean isLastAttempt() {
    return genericRetry.getMaxAttempts() > -1
        && lastAttemptIndex.get() >= genericRetry.getMaxAttempts();
  }

  @Override
  public int getAttemptCount() {
    return lastAttemptIndex.get();
  }
}
