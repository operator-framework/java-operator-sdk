package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.processing.event.EventMarker.EventingState;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter.RateLimitState;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

class ResourceState {
  private final ResourceID id;

  private boolean underProcessing;
  private RetryExecution retry;
  private EventingState eventing;
  private RateLimitState rateLimit;

  public ResourceState(ResourceID id) {
    this.id = id;
  }

  public ResourceID getId() {
    return id;
  }

  public RateLimitState getRateLimit() {
    return rateLimit;
  }

  public void setRateLimit(RateLimitState rateLimit) {
    this.rateLimit = rateLimit;
  }

  public RetryExecution getRetry() {
    return retry;
  }

  public void setRetry(RetryExecution retry) {
    this.retry = retry;
  }

  public boolean isUnderProcessing() {
    return underProcessing;
  }

  public void setUnderProcessing(boolean underProcessing) {
    this.underProcessing = underProcessing;
  }
}
