package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.event.EventHandler;

public abstract class AbstractEventSource implements EventSource {

  protected volatile EventHandler eventHandler;

  @Override
  public void setEventHandler(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }
}
