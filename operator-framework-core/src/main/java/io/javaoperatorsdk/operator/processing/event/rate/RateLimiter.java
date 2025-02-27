package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter.RateLimitState;

public interface RateLimiter<S extends RateLimitState> {
  interface RateLimitState {}

  /**
   * @param rateLimitState state implementation
   * @return empty if permission acquired or minimal duration until a permission could be acquired
   *     again
   */
  Optional<Duration> isLimited(RateLimitState rateLimitState);

  S initState();
}
