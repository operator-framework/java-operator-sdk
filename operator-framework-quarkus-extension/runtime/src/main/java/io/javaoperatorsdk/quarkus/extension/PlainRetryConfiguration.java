package io.javaoperatorsdk.quarkus.extension;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class PlainRetryConfiguration implements RetryConfiguration {

  private final int max;
  private final long initial;
  private final double multiplier;
  private final long maxInterval;

  @RecordableConstructor
  public PlainRetryConfiguration(
      int maxAttempts, long initialInterval, double intervalMultiplier, long maxInterval) {
    this.max = maxAttempts;
    this.initial = initialInterval;
    this.multiplier = intervalMultiplier;
    this.maxInterval = maxInterval;
  }

  @Override
  public int getMaxAttempts() {
    return max;
  }

  @Override
  public long getInitialInterval() {
    return initial;
  }

  @Override
  public double getIntervalMultiplier() {
    return multiplier;
  }

  @Override
  public long getMaxInterval() {
    return maxInterval;
  }
}
