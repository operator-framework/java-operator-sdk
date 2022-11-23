package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

/**
 * Creates an event source to trigger your reconciler whenever something happens to a secondary or
 * external resource that would not normally trigger your reconciler (as the primary resources are
 * not changed). To register EventSources with so that your reconciler is triggered, please make
 * your reconciler implement
 * {@link io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer}.
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
