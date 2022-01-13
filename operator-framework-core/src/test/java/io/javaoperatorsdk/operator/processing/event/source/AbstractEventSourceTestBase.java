package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.AfterEach;

import io.javaoperatorsdk.operator.processing.event.EventHandler;

import static org.mockito.Mockito.mock;

public class AbstractEventSourceTestBase<S extends EventSource, T extends EventHandler> {
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
    setUpSource(source, eventHandler, true);
  }

  public void setUpSource(S source, T eventHandler, boolean start) {
    this.eventHandler = eventHandler;
    this.source = source;
    source.setEventHandler(eventHandler);
    if (start) {
      source.start();
    }
  }
}
