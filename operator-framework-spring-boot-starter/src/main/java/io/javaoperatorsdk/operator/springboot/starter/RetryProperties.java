package io.javaoperatorsdk.operator.springboot.starter;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

public class RetryProperties {

  private Integer maxAttempts;
  private Long initialInterval;
  private Double intervalMultiplier;
  private Long maxInterval;

  public Integer getMaxAttempts() {
    return maxAttempts;
  }

  public RetryProperties setMaxAttempts(Integer maxAttempts) {
    this.maxAttempts = maxAttempts;
    return this;
  }

  public Long getInitialInterval() {
    return initialInterval;
  }

  public RetryProperties setInitialInterval(Long initialInterval) {
    this.initialInterval = initialInterval;
    return this;
  }

  public Double getIntervalMultiplier() {
    return intervalMultiplier;
  }

  public RetryProperties setIntervalMultiplier(Double intervalMultiplier) {
    this.intervalMultiplier = intervalMultiplier;
    return this;
  }

  public Long getMaxInterval() {
    return maxInterval;
  }

  public RetryProperties setMaxInterval(Long maxInterval) {
    this.maxInterval = maxInterval;
    return this;
  }

  public RetryConfiguration asRetryConfiguration() {
    return new RetryConfiguration() {
      @Override
      public int getMaxAttempts() {
        return maxAttempts != null ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
      }

      @Override
      public long getInitialInterval() {
        return initialInterval != null ? initialInterval : DEFAULT_INITIAL_INTERVAL;
      }

      @Override
      public double getIntervalMultiplier() {
        return intervalMultiplier != null ? intervalMultiplier : DEFAULT_MULTIPLIER;
      }

      @Override
      public long getMaxInterval() {
        return maxInterval != null ? maxInterval : RetryConfiguration.DEFAULT.getMaxInterval();
      }
    };
  }
}
