package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter.RateLimitState;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

class ResourceState {

  /**
   * Manages the state of received events. Basically there can be only three distinct states
   * relevant for event processing. Either an event is received, so we eventually process or no
   * event for processing at the moment. The third case is if a DELETE event is received, this is a
   * special case meaning that the custom resource is deleted. We don't want to do any processing
   * anymore so other events are irrelevant for us from this point. Note that the dependant
   * resources are either cleaned up by K8S garbage collection or by the controller implementation
   * for cleanup.
   */
  private enum EventingState {
    EVENT_PRESENT,
    NO_EVENT_PRESENT,
    /** Resource has been marked for deletion, and cleanup already executed successfully */
    PROCESSED_MARK_FOR_DELETION,
    /** Delete event present, from this point other events are not relevant */
    DELETE_EVENT_PRESENT,
  }

  private final ResourceID id;

  private boolean underProcessing;
  private RetryExecution retry;
  private EventingState eventing;
  private RateLimitState rateLimit;
  private HasMetadata lastKnownResource;
  private boolean isDeleteFinalStateUnknown;

  public ResourceState(ResourceID id) {
    this.id = id;
    eventing = EventingState.NO_EVENT_PRESENT;
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

  public void markDeleteEventReceived(
      HasMetadata lastKnownResource, boolean isDeleteFinalStateUnknown) {
    eventing = EventingState.DELETE_EVENT_PRESENT;
    this.lastKnownResource = lastKnownResource;
    this.isDeleteFinalStateUnknown = isDeleteFinalStateUnknown;
  }

  public boolean deleteEventPresent() {
    return eventing == EventingState.DELETE_EVENT_PRESENT;
  }

  public boolean processedMarkForDeletionPresent() {
    return eventing == EventingState.PROCESSED_MARK_FOR_DELETION;
  }

  public void markEventReceived() {
    if (deleteEventPresent()) {
      throw new IllegalStateException("Cannot receive event after a delete event received");
    }
    eventing = EventingState.EVENT_PRESENT;
  }

  public void markProcessedMarkForDeletion() {
    eventing = EventingState.PROCESSED_MARK_FOR_DELETION;
  }

  public boolean eventPresent() {
    return eventing == EventingState.EVENT_PRESENT;
  }

  public boolean noEventPresent() {
    return eventing == EventingState.NO_EVENT_PRESENT;
  }

  public boolean isDeleteFinalStateUnknown() {
    return isDeleteFinalStateUnknown;
  }

  public HasMetadata getLastKnownResource() {
    return lastKnownResource;
  }

  public void unMarkEventReceived() {
    switch (eventing) {
      case EVENT_PRESENT:
        eventing = EventingState.NO_EVENT_PRESENT;
        break;
      case PROCESSED_MARK_FOR_DELETION:
        throw new IllegalStateException("Cannot unmark processed marked for deletion.");
      case DELETE_EVENT_PRESENT:
        throw new IllegalStateException("Cannot unmark delete event.");
      case NO_EVENT_PRESENT:
        // do nothing
        break;
    }
  }

  @Override
  public String toString() {
    return "ResourceState{"
        + "id="
        + id
        + ", underProcessing="
        + underProcessing
        + ", retry="
        + retry
        + ", eventing="
        + eventing
        + ", rateLimit="
        + rateLimit
        + '}';
  }
}
