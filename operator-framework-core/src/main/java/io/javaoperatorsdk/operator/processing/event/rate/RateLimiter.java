package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * A Simple rate limiter per resource.
 */
public class RateLimiter {

  private Duration refreshPeriod;
  private int limitForPeriod;

  private Map<ResourceID, RateState> limitData = new HashMap<>();

  public RateLimiter(Duration refreshPeriod, int limitForPeriod) {
    this.refreshPeriod = refreshPeriod;
    this.limitForPeriod = limitForPeriod;
  }

  /**
   *
   * @param resourceID id of the resource
   * @return empty if permission acquired or minimal duration until a permission could be acquired
   *         again
   */
  public Optional<Duration> acquirePermission(ResourceID resourceID) {
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

  public void clear(ResourceID resourceID) {
    limitData.remove(resourceID);
  }
}
