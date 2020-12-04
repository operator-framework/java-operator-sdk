package io.javaoperatorsdk.operator.processing.event;


public abstract class AbstractEventSource implements EventSource {

  protected EventHandler eventHandler;

  @Override
  public void setEventHandler(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  @Override
  public void eventSourceDeRegisteredForResource(String customResourceUid) {}
}
