package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

public abstract class AbstractEventSource<P extends HasMetadata> implements EventSource<P> {

  private volatile EventSourceRegistry<P> eventSourceRegistry;
  private volatile boolean running = false;

  protected EventHandler getEventHandler() {
    return eventSourceRegistry.getEventHandler();
  }

  @Override
  public void setEventRegistry(EventSourceRegistry<P> registry) {
    this.eventSourceRegistry = registry;
  }

  @Override
  public EventSourceRegistry<P> getEventRegistry() {
    return eventSourceRegistry;
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
