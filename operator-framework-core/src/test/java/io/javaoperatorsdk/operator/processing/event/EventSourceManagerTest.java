package io.javaoperatorsdk.operator.processing.event;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.EventProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EventSourceManagerTest {

  private EventProcessor eventProcessorMock = mock(EventProcessor.class);
  private EventSourceManager eventSourceManager =
      new EventSourceManager();

  @BeforeEach
  public void setup() {
    eventSourceManager.setEventProcessor(eventProcessorMock);
  }

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    eventSourceManager.registerEventSource(eventSource);

    Set<EventSource> registeredSources =
        eventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources).hasSize(2);

    verify(eventSource, times(1)).setEventHandler(eq(eventProcessorMock));
  }

  @Test
  public void closeShouldCascadeToEventSources() throws IOException {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(EventSource.class);
    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.stop();

    verify(eventSource, times(1)).stop();
    verify(eventSource2, times(1)).stop();
  }

  @Test
  public void startCascadesToEventSources() {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(EventSource.class);
    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.start();

    verify(eventSource, times(1)).start();
    verify(eventSource2, times(1)).start();
  }

  @Test
  public void deRegistersEventSources() {
    CustomResource customResource = TestUtils.testCustomResource();
    EventSource eventSource = mock(EventSource.class);
    eventSourceManager.registerEventSource(eventSource);

    eventSourceManager
        .cleanupForCustomResource(ResourceID.fromResource(customResource));

    verify(eventSource, times(1))
        .cleanupForResource(eq(ResourceID.fromResource(customResource)));
  }
}
