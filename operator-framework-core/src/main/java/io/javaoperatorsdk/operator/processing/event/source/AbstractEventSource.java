package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

public abstract class AbstractEventSource<P extends HasMetadata> implements EventSource<P> {

  private volatile EventSourceRegistry<P> eventSourceRegistry;
  private final Class<?> resourceClass;

  protected AbstractEventSource(Class<?> resourceClass) {
    this.resourceClass = resourceClass;
  }

  @Override
  public Class<?> getResourceClass() {
    return resourceClass;
  }

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

}
