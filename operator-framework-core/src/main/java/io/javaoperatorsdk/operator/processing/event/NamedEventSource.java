package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

class NamedEventSource implements EventSource {

  private final EventSource original;
  private final String name;

  NamedEventSource(EventSource original, String name) {
    this.original = original;
    this.name = name;
  }

  @Override
  public void start() throws OperatorException {
    original.start();
  }

  @Override
  public void stop() throws OperatorException {
    original.stop();
  }

  @Override
  public void setEventHandler(EventHandler handler) {
    original.setEventHandler(handler);
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return original + " named: '" + name + "'}";
  }

  public EventSource original() {
    return original;
  }
}
