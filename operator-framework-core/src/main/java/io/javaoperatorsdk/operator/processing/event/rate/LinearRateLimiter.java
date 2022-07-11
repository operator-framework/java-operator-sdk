package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * A simple rate limiter that limits the number of permission for a time interval.
 */
public class LinearRateLimiter
    implements RateLimiter, AnnotationConfigurable<RateLimited> {

  /** To turn off rate limiting set limit for period to a non-positive number */
  public static final int NO_LIMIT_PERIOD = -1;

  private Duration refreshPeriod;
  private int limitForPeriod = NO_LIMIT_PERIOD;

  private final Map<ResourceID, RateState> limitData = new HashMap<>();

  public static LinearRateLimiter deactivatedRateLimiter() {
    return new LinearRateLimiter();
  }

  LinearRateLimiter() {}

  public LinearRateLimiter(Duration refreshPeriod, int limitForPeriod) {
    this.refreshPeriod = refreshPeriod;
    this.limitForPeriod = limitForPeriod;
  }

  @Override
  public Optional<Duration> acquirePermission(ResourceID resourceID) {
    if (!isActivated()) {
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

  @Override
  public void initFrom(RateLimited configuration) {
    this.refreshPeriod = Duration.of(configuration.within(),
        configuration.unit().toChronoUnit());
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
