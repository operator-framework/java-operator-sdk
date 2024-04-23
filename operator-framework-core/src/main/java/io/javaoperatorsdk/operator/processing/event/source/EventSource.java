package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

/**
 * Creates an event source to trigger your reconciler whenever something happens to a secondary or
 * external resource that should cause a reconciliation of the primary resource. EventSource
 * generalizes the concept of Informers and extends it to external (i.e. non Kubernetes) resources.
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

  default String name() {
    return generateName(this);
  }

  static String generateName(EventSource eventSource) {
    return eventSource.getClass().getName() + "@" + Integer.toHexString(eventSource.hashCode());
  }

}
