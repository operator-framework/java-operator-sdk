package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;

/** A simple rate limiter that limits the number of permission for a time interval. */
public class LinearRateLimiter
    implements RateLimiter<RateState>, AnnotationConfigurable<RateLimited> {

  /** To turn off rate limiting set limit for period to a non-positive number */
  public static final int NO_LIMIT_PERIOD = -1;

  public static final int DEFAULT_REFRESH_PERIOD_SECONDS = 10;
  public static final Duration DEFAULT_REFRESH_PERIOD =
      Duration.ofSeconds(DEFAULT_REFRESH_PERIOD_SECONDS);

  private Duration refreshPeriod;
  private int limitForPeriod;

  public static LinearRateLimiter deactivatedRateLimiter() {
    return new LinearRateLimiter();
  }

  public LinearRateLimiter() {
    this(DEFAULT_REFRESH_PERIOD, NO_LIMIT_PERIOD);
  }

  public LinearRateLimiter(Duration refreshPeriod, int limitForPeriod) {
    this.refreshPeriod = refreshPeriod;
    this.limitForPeriod = limitForPeriod;
  }

  @Override
  public Optional<Duration> isLimited(RateLimitState rateLimitState) {
    if (!isActivated() || !(rateLimitState instanceof RateState actualState)) {
      return Optional.empty();
    }

    if (actualState.getCount() < limitForPeriod) {
      actualState.increaseCount();
      return Optional.empty();
    } else if (actualState
        .getLastRefreshTime()
        .isBefore(LocalDateTime.now().minus(refreshPeriod))) {
      actualState.reset();
      actualState.increaseCount();
      return Optional.empty();
    } else {
      return Optional.of(Duration.between(actualState.getLastRefreshTime(), LocalDateTime.now()));
    }
  }

  @Override
  public RateState initState() {
    return RateState.initialState();
  }

  @Override
  public void initFrom(RateLimited configuration) {
    this.refreshPeriod = Duration.of(configuration.within(), configuration.unit().toChronoUnit());
    this.limitForPeriod = configuration.maxReconciliations();
  }

  public boolean isActivated() {
    return limitForPeriod > 0;
  }

  public int getLimitForPeriod() {
    return limitForPeriod;
  }

  public Duration getRefreshPeriod() {
    return refreshPeriod;
  }
}
