package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

/**
 * Creates an event source to trigger your reconciler whenever something happens to a secondary or
 * external resource that would not normally trigger your reconciler (as the primary resources are
 * not changed). To register EventSources with so that your reconciler is triggered, please make
 * your reconciler implement
 * {@link io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer}.
 */
public interface EventSource extends LifecycleAware {

  /**
   * An optional name for your EventSource. This is only required if you need to register multiple
   * EventSources for the same resource type (e.g. {@code Deployment}).
   *
   * @return the name associated with this EventSource
   */
  default String name() {
    return getClass().getCanonicalName();
  }

  /**
   * Sets the {@link EventHandler} that is linked to your reconciler when this EventSource is
   * registered.
   *
   * @param handler the {@link EventHandler} associated with your reconciler
   */
  void setEventHandler(EventHandler handler);
}
