package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * A Simple rate limiter that limits the number of permission for a time interval.
 */
public class PeriodRateLimiter implements RateLimiter {

  public static final int DEFAULT_REFRESH_PERIOD_SECONDS = 2;
  public static final Duration DEFAULT_REFRESH_PERIOD =
      Duration.ofSeconds(DEFAULT_REFRESH_PERIOD_SECONDS);

  public static final int DEFAULT_LIMIT_FOR_PERIOD = 3;
  /** To turn off rate limiting set limit fod period to a non-positive number */
  public static final int NO_LIMIT_PERIOD = -1;

  private Duration refreshPeriod;
  private int limitForPeriod;

  private Map<ResourceID, RateState> limitData = new HashMap<>();

  public PeriodRateLimiter() {
    this(DEFAULT_REFRESH_PERIOD, DEFAULT_LIMIT_FOR_PERIOD);
  }

  public PeriodRateLimiter(Duration refreshPeriod, int limitForPeriod) {
    this.refreshPeriod = refreshPeriod;
    this.limitForPeriod = limitForPeriod;
  }

  /**
   * @param resourceID id of the resource
   * @return empty if permission acquired or minimal duration until a permission could be acquired
   *         again
   */
  @Override
  public Optional<Duration> acquirePermission(ResourceID resourceID) {
    if (limitForPeriod <= 0) {
      return Optional.empty();
    }
    var actualState = limitData.computeIfAbsent(resourceID, r -> RateState.initialState());
    if (actualState.getCount() < limitForPeriod) {
      actualState.increaseCount();
      return Optional.empty();
    } else if (actualState.getLastRefreshTime()
        .isBefore(LocalDateTime.now().minus(refreshPeriod))) {
      actualState.reset();
      actualState.increaseCount();
      return Optional.empty();
    } else {
      return Optional.of(Duration.between(actualState.getLastRefreshTime(), LocalDateTime.now()));
    }
  }

  @Override
  public void clear(ResourceID resourceID) {
    limitData.remove(resourceID);
  }
}
