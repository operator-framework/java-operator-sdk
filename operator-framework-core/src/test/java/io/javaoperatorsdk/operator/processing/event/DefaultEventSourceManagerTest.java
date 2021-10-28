package io.javaoperatorsdk.operator.processing.event;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DefaultEventSourceManagerTest {

  private DefaultEventHandler defaultEventHandlerMock = mock(DefaultEventHandler.class);
  private DefaultEventSourceManager defaultEventSourceManager =
      new DefaultEventSourceManager(defaultEventHandlerMock);

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    defaultEventSourceManager.registerEventSource(eventSource);

    Set<EventSource> registeredSources =
        defaultEventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources).hasSize(2);

    verify(eventSource, times(1)).setEventHandler(eq(defaultEventHandlerMock));
    verify(eventSource, times(1)).start();
  }

  @Test
  public void closeShouldCascadeToEventSources() throws IOException {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(EventSource.class);
    defaultEventSourceManager.registerEventSource(eventSource);
    defaultEventSourceManager.registerEventSource(eventSource2);

    defaultEventSourceManager.close();

    verify(eventSource, times(1)).close();
    verify(eventSource2, times(1)).close();
  }

  @Test
  public void deRegistersEventSources() {
    CustomResource customResource = TestUtils.testCustomResource();
    EventSource eventSource = mock(EventSource.class);
    defaultEventSourceManager.registerEventSource(eventSource);

    defaultEventSourceManager
        .cleanupForCustomResource(CustomResourceID.fromResource(customResource));

    verify(eventSource, times(1))
        .cleanupForCustomResource(eq(CustomResourceID.fromResource(customResource)));
  }
}
