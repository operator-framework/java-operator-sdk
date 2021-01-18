package io.javaoperatorsdk.operator.processing.event;

public interface EventSource {

  void setEventHandler(EventHandler eventHandler);

  void eventSourceDeRegisteredForResource(String customResourceUid);

  default void close() {};
}
