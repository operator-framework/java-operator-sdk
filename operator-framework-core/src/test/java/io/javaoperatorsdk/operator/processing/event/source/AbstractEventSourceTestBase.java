package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.AfterEach;

import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
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


  public void setUpSource(S source, boolean start, ConfigurationService configurationService) {
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
    setUpSource(source, eventHandler, start, new BaseConfigurationService());
  }

  public void setUpSource(S source, T eventHandler, boolean start,
      ConfigurationService configurationService) {
    this.eventHandler = eventHandler;
    this.source = source;

    if (source instanceof ManagedInformerEventSource) {
      ((ManagedInformerEventSource) source).setConfigurationService(configurationService);
    }

    source.setEventHandler(eventHandler);

    if (start) {
      source.start();
    }
  }
}
