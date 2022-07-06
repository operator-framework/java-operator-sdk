package io.javaoperatorsdk.operator.processing.retry;

import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

public class GenericRetry implements Retry, AnnotationConfigurable<RetryConfiguration> {
  private int maxAttempts = RetryConfiguration.DEFAULT_MAX_ATTEMPTS;
  private long initialInterval = RetryConfiguration.DEFAULT_INITIAL_INTERVAL;
  private double intervalMultiplier = RetryConfiguration.DEFAULT_MULTIPLIER;
  private long maxInterval = RetryConfiguration.DEFAULT_MAX_INTERVAL;

  public static GenericRetry defaultLimitedExponentialRetry() {
    return new GenericRetry();
  }

  public static GenericRetry noRetry() {
    return new GenericRetry().setMaxAttempts(0);
  }

  public static GenericRetry every10second10TimesRetry() {
    return new GenericRetry().withLinearRetry().setMaxAttempts(10).setInitialInterval(10000);
  }

  @Override
  public GenericRetryExecution initExecution() {
    return new GenericRetryExecution(this);
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public GenericRetry setMaxAttempts(int maxRetryAttempts) {
    this.maxAttempts = maxRetryAttempts;
    return this;
  }

  public long getInitialInterval() {
    return initialInterval;
  }

  public GenericRetry setInitialInterval(long initialInterval) {
    this.initialInterval = initialInterval;
    return this;
  }

  public double getIntervalMultiplier() {
    return intervalMultiplier;
  }

  public GenericRetry setIntervalMultiplier(double intervalMultiplier) {
    this.intervalMultiplier = intervalMultiplier;
    return this;
  }

  public long getMaxInterval() {
    return maxInterval;
  }

  public GenericRetry setMaxInterval(long maxInterval) {
    this.maxInterval = maxInterval;
    return this;
  }

  public GenericRetry withoutMaxInterval() {
    this.maxInterval = -1;
    return this;
  }

  public GenericRetry withoutMaxAttempts() {
    return this.setMaxAttempts(-1);
  }

  public GenericRetry withLinearRetry() {
    this.intervalMultiplier = 1;
    return this;
  }

  @Override
  public void initFrom(RetryConfiguration configuration) {
    this.initialInterval = configuration.initialInterval();
    this.maxAttempts = configuration.maxAttempts();
    this.intervalMultiplier = configuration.intervalMultiplier();
    this.maxInterval = configuration.maxInterval() == RetryConfiguration.UNSET_VALUE
        ? RetryConfiguration.DEFAULT_MAX_INTERVAL
        : configuration.maxInterval();
  }
}
