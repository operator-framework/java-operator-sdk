package io.javaoperatorsdk.operator.processing.event.source;


import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

public abstract class AbstractEventSource implements EventSource {
  private EventHandler handler;
  private volatile boolean running = false;
  private EventSourceStartPriority eventSourceStartPriority = EventSourceStartPriority.DEFAULT;
  private final String name;

  protected AbstractEventSource() {
    this(null);
  }

  protected AbstractEventSource(String name) {
    this.name = name == null ? EventSource.super.name() : name;
  }

  @Override
  public String name() {
    return name;
  }

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

  @Override
  public EventSourceStartPriority priority() {
    return eventSourceStartPriority;
  }

  public AbstractEventSource setEventSourcePriority(
      EventSourceStartPriority eventSourceStartPriority) {
    this.eventSourceStartPriority = eventSourceStartPriority;
    return this;
  }

}
