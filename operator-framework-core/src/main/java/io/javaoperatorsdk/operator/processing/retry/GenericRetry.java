package io.javaoperatorsdk.operator.processing.retry;

import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

public class GenericRetry implements Retry, AnnotationConfigurable<RetryingGradually> {
  private int maxAttempts = RetryingGradually.DEFAULT_MAX_ATTEMPTS;
  private long initialInterval = RetryingGradually.DEFAULT_INITIAL_INTERVAL;
  private double intervalMultiplier = RetryingGradually.DEFAULT_MULTIPLIER;
  private long maxInterval = RetryingGradually.DEFAULT_MAX_INTERVAL;

  public static final Retry DEFAULT = fromConfiguration(RetryConfiguration.DEFAULT);

  public static GenericRetry defaultLimitedExponentialRetry() {
    return new GenericRetry();
  }

  public static GenericRetry noRetry() {
    return new GenericRetry().setMaxAttempts(0);
  }

  /**
   * @deprecated Use the {@link RetryingGradually} annotation instead
   */
  @Deprecated(forRemoval = true)
  public static Retry fromConfiguration(RetryConfiguration configuration) {
    return configuration == null ? defaultLimitedExponentialRetry()
        : new GenericRetry()
            .setInitialInterval(configuration.getInitialInterval())
            .setMaxAttempts(configuration.getMaxAttempts())
            .setIntervalMultiplier(configuration.getIntervalMultiplier())
            .setMaxInterval(configuration.getMaxInterval());
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
  public void initFrom(RetryingGradually configuration) {
    this.initialInterval = configuration.initialInterval();
    this.maxAttempts = configuration.maxAttempts();
    this.intervalMultiplier = configuration.intervalMultiplier();
    this.maxInterval = configuration.maxInterval() == RetryingGradually.UNSET_VALUE
        ? RetryingGradually.DEFAULT_MAX_INTERVAL
        : configuration.maxInterval();
  }
}
