package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

/**
 * Creates an event source to trigger your reconciler. EventSource is generalized concept of
 * Informer to cover also external resources.
 */
public interface EventSource extends LifecycleAware, EventSourceHealthIndicator {

  /**
   * Sets the {@link EventHandler} that is linked to your reconciler when this EventSource is
   * registered.
   *
   * @param handler the {@link EventHandler} associated with your reconciler
   */
  void setEventHandler(EventHandler handler);

  default EventSourceStartPriority priority() {
    return EventSourceStartPriority.DEFAULT;
  }

  @Override
  default Status getStatus() {
    return Status.UNKNOWN;
  }
}
