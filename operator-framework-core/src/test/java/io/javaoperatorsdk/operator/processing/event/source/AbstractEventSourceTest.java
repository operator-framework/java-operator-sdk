package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.AfterEach;

import io.javaoperatorsdk.operator.processing.event.EventHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractEventSourceTest<S extends EventSource, T extends EventHandler> {
  protected T eventHandler;
  protected S source;

  @AfterEach
  public void tearDown() {
    source.stop();
  }

  public void setUpSource(S source) {
    setUpSource(source, true);
  }

  public void setUpSource(S source, boolean start) {
    setUpSource(source, (T) mock(EventHandler.class), start);
  }

  public void setUpSource(S source, T eventHandler) {
    this.eventHandler = eventHandler;
    this.source = source;
    EventSourceRegistry registry = mock(EventSourceRegistry.class);
    when(registry.getEventHandler()).thenReturn(eventHandler);
    source.setEventRegistry(registry);
    source.start();
  }

  public void setUpSource(S source, T eventHandler, boolean start) {
    this.eventHandler = eventHandler;
    this.source = source;
    EventSourceRegistry registry = mock(EventSourceRegistry.class);
    when(registry.getEventHandler()).thenReturn(eventHandler);
    source.setEventRegistry(registry);
    if (start) {
      source.start();
    }
  }
}
