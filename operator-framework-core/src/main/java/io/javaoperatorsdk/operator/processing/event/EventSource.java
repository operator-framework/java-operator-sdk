package io.javaoperatorsdk.operator.processing.event;

public interface EventSource extends AutoCloseable {

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

  void eventSourceDeRegisteredForResource(String customResourceUid);
}
