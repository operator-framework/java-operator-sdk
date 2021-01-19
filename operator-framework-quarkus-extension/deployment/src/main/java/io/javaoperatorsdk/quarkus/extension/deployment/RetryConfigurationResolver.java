package io.javaoperatorsdk.quarkus.extension.deployment;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.quarkus.extension.ExternalIntervalConfiguration;
import io.javaoperatorsdk.quarkus.extension.ExternalRetryConfiguration;
import io.javaoperatorsdk.quarkus.extension.PlainRetryConfiguration;
import java.util.Optional;

class RetryConfigurationResolver implements RetryConfiguration {

  private final RetryConfiguration delegate;

  private RetryConfigurationResolver(Optional<ExternalRetryConfiguration> retry) {
    delegate =
        retry
            .<RetryConfiguration>map(ExternalRetryConfigurationAdapter::new)
            .orElse(RetryConfiguration.DEFAULT);
  }

  public static RetryConfiguration resolve(Optional<ExternalRetryConfiguration> retry) {
    final var delegate = new RetryConfigurationResolver(retry);
    return new PlainRetryConfiguration(
        delegate.getMaxAttempts(),
        delegate.getInitialInterval(),
        delegate.getIntervalMultiplier(),
        delegate.getMaxInterval());
  }

  @Override
  public int getMaxAttempts() {
    return delegate.getMaxAttempts();
  }

  @Override
  public long getInitialInterval() {
    return delegate.getInitialInterval();
  }

  @Override
  public double getIntervalMultiplier() {
    return delegate.getIntervalMultiplier();
  }

  @Override
  public long getMaxInterval() {
    return delegate.getMaxInterval();
  }

  private static class ExternalRetryConfigurationAdapter implements RetryConfiguration {

    private final int maxAttempts;
    private final IntervalConfigurationAdapter interval;

    public ExternalRetryConfigurationAdapter(ExternalRetryConfiguration config) {
      maxAttempts = config.maxAttempts.orElse(RetryConfiguration.DEFAULT.getMaxAttempts());
      interval =
          config
              .interval
              .map(IntervalConfigurationAdapter::new)
              .orElse(new IntervalConfigurationAdapter());
    }

    @Override
    public int getMaxAttempts() {
      return maxAttempts;
    }

    @Override
    public long getInitialInterval() {
      return interval.initial;
    }

    @Override
    public double getIntervalMultiplier() {
      return interval.multiplier;
    }

    @Override
    public long getMaxInterval() {
      return interval.max;
    }
  }

  private static class IntervalConfigurationAdapter {

    private final long initial;
    private final double multiplier;
    private final long max;

    IntervalConfigurationAdapter(ExternalIntervalConfiguration config) {
      initial = config.initial.orElse(RetryConfiguration.DEFAULT.getInitialInterval());
      multiplier = config.multiplier.orElse(RetryConfiguration.DEFAULT.getIntervalMultiplier());
      max = config.max.orElse(RetryConfiguration.DEFAULT.getMaxInterval());
    }

    IntervalConfigurationAdapter() {
      this.initial = RetryConfiguration.DEFAULT.getInitialInterval();
      this.multiplier = RetryConfiguration.DEFAULT.getIntervalMultiplier();
      this.max = RetryConfiguration.DEFAULT.getMaxInterval();
    }
  }
}
