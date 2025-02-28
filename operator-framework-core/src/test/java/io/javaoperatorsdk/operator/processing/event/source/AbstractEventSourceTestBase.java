package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.AfterEach;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

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

  public void setUpSource(S source, boolean start, ControllerConfiguration configurationService) {
    setUpSource(source, (T) mock(EventHandler.class), start, configurationService);
  }

  @SuppressWarnings("unchecked")
  public void setUpSource(S source, boolean start) {
    setUpSource(source, (T) mock(EventHandler.class), start);
  }

  public void setUpSource(S source, T eventHandler) {
    setUpSource(source, eventHandler, true);
  }

  public void setUpSource(S source, T eventHandler, boolean start) {
    setUpSource(source, eventHandler, start, mock(ControllerConfiguration.class));
  }

  public void setUpSource(
      S source, T eventHandler, boolean start, ControllerConfiguration controllerConfiguration) {
    this.eventHandler = eventHandler;
    this.source = source;

    if (source instanceof ManagedInformerEventSource) {
      ((ManagedInformerEventSource) source).setControllerConfiguration(controllerConfiguration);
    }

    source.setEventHandler(eventHandler);

    if (start) {
      source.start();
    }
  }
}
