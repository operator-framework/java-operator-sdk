package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

public abstract class AbstractEventSource implements EventSource {

  private EventHandler handler;
  private volatile boolean running = false;

  protected EventHandler getEventHandler() {
    return handler;
  }

  @Override
  public void setEventHandler(EventHandler handler) {
    this.handler = handler;
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public void start() throws OperatorException {
    running = true;
  }

  @Override
  public void stop() throws OperatorException {
    running = false;
  }
}
