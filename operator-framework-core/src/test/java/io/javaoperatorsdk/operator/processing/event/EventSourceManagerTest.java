package io.javaoperatorsdk.operator.processing.event;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EventSourceManagerTest {

  private EventSourceManager eventSourceManager =
      new EventSourceManager(mock(EventProcessor.class));

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    eventSourceManager.registerEventSource(eventSource);

    Set<EventSource> registeredSources = eventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources).contains(eventSource);

    verify(eventSource, times(1)).setEventSourceRegistry(eq(eventSourceManager));
  }

  @Test
  public void closeShouldCascadeToEventSources() throws IOException {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(TimerEventSource.class);

    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.stop();

    verify(eventSource, times(1)).stop();
    verify(eventSource2, times(1)).stop();
  }

  @Test
  public void startCascadesToEventSources() {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(TimerEventSource.class);
    eventSourceManager.registerEventSource(eventSource);
    eventSourceManager.registerEventSource(eventSource2);

    eventSourceManager.start();

    verify(eventSource, times(1)).start();
    verify(eventSource2, times(1)).start();
  }
}
