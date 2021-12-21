package io.javaoperatorsdk.operator.processing.retry;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

public class GenericRetry implements Retry {
  private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
  private long initialInterval = DEFAULT_INITIAL_INTERVAL;
  private double intervalMultiplier = DEFAULT_MULTIPLIER;
  private long maxInterval = -1;

  public static GenericRetry defaultLimitedExponentialRetry() {
    return new GenericRetry();
  }

  public static GenericRetry noRetry() {
    return new GenericRetry().setMaxAttempts(0);
  }

  public static GenericRetry every10second10TimesRetry() {
    return new GenericRetry().withLinearRetry().setMaxAttempts(10).setInitialInterval(10000);
  }

  public static Retry fromConfiguration(RetryConfiguration configuration) {
    return configuration == null ? defaultLimitedExponentialRetry()
        : new GenericRetry()
            .setInitialInterval(configuration.getInitialInterval())
            .setMaxAttempts(configuration.getMaxAttempts())
            .setIntervalMultiplier(configuration.getIntervalMultiplier())
            .setMaxInterval(configuration.getMaxInterval());
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
}
