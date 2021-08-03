package io.javaoperatorsdk.operator.processing.event;

import java.io.Closeable;

public interface EventSource extends Closeable {

  /**
   * This method is invoked when this {@link EventSource} instance is properly registered to a
   * {@link EventSourceManager}.
   */
  default void start() {}

  /**
   * This method is invoked when this {@link EventSource} instance is de-registered from a {@link
   * EventSourceManager}.
   */
  @Override
  default void close() {}

  void setEventHandler(EventHandler eventHandler);

  default void eventSourceDeRegisteredForResource(String customResourceUid) {}
}
