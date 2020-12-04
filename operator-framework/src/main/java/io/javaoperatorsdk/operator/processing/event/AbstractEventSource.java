package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.api.EventHandler;
import io.javaoperatorsdk.operator.api.EventSource;

public abstract class AbstractEventSource implements EventSource {

  protected EventHandler eventHandler;

  @Override
  public void setEventHandler(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  @Override
  public void eventSourceDeRegisteredForResource(String customResourceUid) {}
}
