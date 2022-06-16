package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.Duration;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface RateLimiter {

  Optional<Duration> acquirePermission(ResourceID resourceID);

  void clear(ResourceID resourceID);

}
