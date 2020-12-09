package io.javaoperatorsdk.operator.api;

public interface EventSource {

  void setEventHandler(EventHandler eventHandler);

  void eventSourceDeRegisteredForResource(String customResourceUid);
}
