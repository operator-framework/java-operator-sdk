package io.javaoperatorsdk.operator.processing.event;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DefaultEventSourceManagerTest {

  public static final String CUSTOM_EVENT_SOURCE_NAME = "CustomEventSource";

  private DefaultEventHandler defaultEventHandlerMock = mock(DefaultEventHandler.class);
  private DefaultEventSourceManager defaultEventSourceManager =
      new DefaultEventSourceManager(defaultEventHandlerMock);

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME, eventSource);

    Map<String, EventSource> registeredSources =
        defaultEventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources.entrySet()).hasSize(2);
    assertThat(registeredSources.get(CUSTOM_EVENT_SOURCE_NAME)).isEqualTo(eventSource);
    verify(eventSource, times(1)).setEventHandler(eq(defaultEventHandlerMock));
    verify(eventSource, times(1)).start();
  }

  @Test
  public void closeShouldCascadeToEventSources() throws IOException {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(EventSource.class);
    defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME, eventSource);
    defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME + "2", eventSource2);

    defaultEventSourceManager.close();

    verify(eventSource, times(1)).close();
    verify(eventSource2, times(1)).close();
  }

  @Test
  public void throwExceptionIfRegisteringEventSourceWithSameName() {
    EventSource eventSource = mock(EventSource.class);
    EventSource eventSource2 = mock(EventSource.class);

    defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME, eventSource);
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () -> {
              defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME, eventSource2);
            });
  }

  @Test
  public void deRegistersEventSources() {
    CustomResource customResource = TestUtils.testCustomResource();
    EventSource eventSource = mock(EventSource.class);
    defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME, eventSource);

    defaultEventSourceManager.deRegisterCustomResourceFromEventSource(
        CUSTOM_EVENT_SOURCE_NAME, CustomResourceID.fromResource(customResource));

    verify(eventSource, times(1))
        .eventSourceDeRegisteredForResource(eq(CustomResourceID.fromResource(customResource)));
  }
}
