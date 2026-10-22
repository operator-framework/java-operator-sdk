/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.retry;

import java.time.Duration;

import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;

public class GenericRetry implements Retry, AnnotationConfigurable<GradualRetry> {
  private int maxAttempts = GradualRetry.DEFAULT_MAX_ATTEMPTS;
  private long initialInterval = GradualRetry.DEFAULT_INITIAL_INTERVAL;
  private double intervalMultiplier = GradualRetry.DEFAULT_MULTIPLIER;
  private long maxInterval = GradualRetry.DEFAULT_MAX_INTERVAL;

  /**
   * @deprecated use {@link GenericRetry#defaultLimitedExponentialRetry()} instead this instance.
   *     Since GenericRetry is mutable, singleton is problematic.
   */
  @Deprecated(forRemoval = true)
  public static final Retry DEFAULT = new GenericRetry();

  /**
   * Creates an instance with the default values. Prefer {@link #builder()} or {@link
   * #defaultLimitedExponentialRetry()}; this constructor is kept public so that the class can be
   * instantiated and configured from the {@link GradualRetry} annotation.
   */
  public GenericRetry() {}

  private GenericRetry(Builder builder) {
    this.maxAttempts = builder.maxAttempts;
    this.initialInterval = builder.initialInterval;
    this.intervalMultiplier = builder.intervalMultiplier;
    this.maxInterval = builder.maxInterval;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static GenericRetry defaultLimitedExponentialRetry() {
    return builder().build();
  }

  public static GenericRetry noRetry() {
    return builder().withMaxAttempts(0).build();
  }

  public static GenericRetry every10second10TimesRetry() {
    return builder()
        .withLinearRetry()
        .withMaxAttempts(10)
        .withInitialInterval(Duration.ofSeconds(10))
        .build();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public GenericRetryExecution initExecution() {
    return new GenericRetryExecution(this);
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  /**
   * @deprecated use {@link Builder#withMaxAttempts(int)} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry setMaxAttempts(int maxRetryAttempts) {
    this.maxAttempts = maxRetryAttempts;
    return this;
  }

  public long getInitialInterval() {
    return initialInterval;
  }

  /**
   * @deprecated use {@link Builder#withInitialInterval(Duration)} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry setInitialInterval(Duration initialInterval) {
    this.initialInterval = initialInterval.toMillis();
    return this;
  }

  /**
   * @deprecated use {@link Builder#withInitialInterval(long)} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry setInitialInterval(long initialInterval) {
    this.initialInterval = initialInterval;
    return this;
  }

  public double getIntervalMultiplier() {
    return intervalMultiplier;
  }

  /**
   * @deprecated use {@link Builder#withIntervalMultiplier(double)} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry setIntervalMultiplier(double intervalMultiplier) {
    this.intervalMultiplier = intervalMultiplier;
    return this;
  }

  public long getMaxInterval() {
    return maxInterval;
  }

  /**
   * @deprecated use {@link Builder#withMaxInterval(long)} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry setMaxInterval(long maxInterval) {
    this.maxInterval = maxInterval;
    return this;
  }

  /**
   * @deprecated use {@link Builder#withMaxInterval(Duration)} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry setMaxInterval(Duration maxInterval) {
    this.maxInterval = maxInterval.toMillis();
    return this;
  }

  /**
   * @deprecated use {@link Builder#withoutMaxInterval()} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry withoutMaxInterval() {
    this.maxInterval = -1;
    return this;
  }

  /**
   * @deprecated use {@link Builder#withoutMaxAttempts()} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry withoutMaxAttempts() {
    this.maxAttempts = -1;
    return this;
  }

  /**
   * @deprecated use {@link Builder#withLinearRetry()} instead
   */
  @Deprecated(forRemoval = true)
  public GenericRetry withLinearRetry() {
    this.intervalMultiplier = 1;
    return this;
  }

  @Override
  public void initFrom(GradualRetry configuration) {
    this.initialInterval = configuration.initialInterval();
    this.maxAttempts = configuration.maxAttempts();
    this.intervalMultiplier = configuration.intervalMultiplier();
    this.maxInterval =
        configuration.maxInterval() == GradualRetry.UNSET_VALUE
            ? GradualRetry.DEFAULT_MAX_INTERVAL
            : configuration.maxInterval();
  }

  @Override
  public String toString() {
    return "GenericRetry{maxAttempts="
        + maxAttempts
        + ", initialInterval="
        + initialInterval
        + ", intervalMultiplier="
        + intervalMultiplier
        + ", maxInterval="
        + maxInterval
        + '}';
  }

  public static final class Builder {
    private int maxAttempts = GradualRetry.DEFAULT_MAX_ATTEMPTS;
    private long initialInterval = GradualRetry.DEFAULT_INITIAL_INTERVAL;
    private double intervalMultiplier = GradualRetry.DEFAULT_MULTIPLIER;
    private long maxInterval = GradualRetry.DEFAULT_MAX_INTERVAL;

    private Builder() {}

    private Builder(GenericRetry retry) {
      this.maxAttempts = retry.maxAttempts;
      this.initialInterval = retry.initialInterval;
      this.intervalMultiplier = retry.intervalMultiplier;
      this.maxInterval = retry.maxInterval;
    }

    public Builder withMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    public Builder withoutMaxAttempts() {
      return withMaxAttempts(-1);
    }

    public Builder withInitialInterval(long initialIntervalMillis) {
      this.initialInterval = initialIntervalMillis;
      return this;
    }

    public Builder withInitialInterval(Duration initialInterval) {
      return withInitialInterval(initialInterval.toMillis());
    }

    public Builder withIntervalMultiplier(double intervalMultiplier) {
      this.intervalMultiplier = intervalMultiplier;
      return this;
    }

    public Builder withLinearRetry() {
      return withIntervalMultiplier(1);
    }

    public Builder withMaxInterval(long maxIntervalMillis) {
      this.maxInterval = maxIntervalMillis;
      return this;
    }

    public Builder withMaxInterval(Duration maxInterval) {
      return withMaxInterval(maxInterval.toMillis());
    }

    public Builder withoutMaxInterval() {
      return withMaxInterval(-1);
    }

    public GenericRetry build() {
      return new GenericRetry(this);
    }
  }
}
