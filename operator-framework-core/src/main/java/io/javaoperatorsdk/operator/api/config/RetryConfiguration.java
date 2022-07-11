package io.javaoperatorsdk.operator.api.config;

import io.javaoperatorsdk.operator.processing.retry.GradualRetry;

/**
 * @deprecated specify your own {@link io.javaoperatorsdk.operator.processing.retry.Retry}
 *             implementation or use {@link GradualRetry} annotation instead
 */
@Deprecated(forRemoval = true)
public interface RetryConfiguration {

  RetryConfiguration DEFAULT = new DefaultRetryConfiguration();

  int DEFAULT_MAX_ATTEMPTS = 5;
  long DEFAULT_INITIAL_INTERVAL = 2000L;
  double DEFAULT_MULTIPLIER = 1.5D;

  default int getMaxAttempts() {
    return DEFAULT_MAX_ATTEMPTS;
  }

  default long getInitialInterval() {
    return DEFAULT_INITIAL_INTERVAL;
  }

  default double getIntervalMultiplier() {
    return DEFAULT_MULTIPLIER;
  }

  default long getMaxInterval() {
    return (long) (DEFAULT_INITIAL_INTERVAL * Math.pow(DEFAULT_MULTIPLIER, DEFAULT_MAX_ATTEMPTS));
  }
}
