package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface RateLimiter {

  /**
   * @param resourceID id of the resource
   * @return empty if permission acquired or minimal duration until a permission could be acquired
   *         again
   */
  Optional<Duration> acquirePermission(ResourceID resourceID);

  void clear(ResourceID resourceID);

}
