package io.javaoperatorsdk.operator.processing.event;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.KubernetesResourceUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultEventSourceManagerTest {

  public static final String CUSTOM_EVENT_SOURCE_NAME = "CustomEventSource";

  private DefaultEventHandler defaultEventHandlerMock = mock(DefaultEventHandler.class);
  private DefaultEventSourceManager defaultEventSourceManager =
      new DefaultEventSourceManager(defaultEventHandlerMock, false);

  @Test
  public void registersEventSource() {
    EventSource eventSource = mock(EventSource.class);

    defaultEventSourceManager.registerEventSource(CUSTOM_EVENT_SOURCE_NAME, eventSource);

    Map<String, EventSource> registeredSources =
        defaultEventSourceManager.getRegisteredEventSources();
    assertThat(registeredSources.entrySet()).hasSize(1);
    assertThat(registeredSources.get(CUSTOM_EVENT_SOURCE_NAME)).isEqualTo(eventSource);
    verify(eventSource, times(1)).setEventHandler(eq(defaultEventHandlerMock));
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
        CUSTOM_EVENT_SOURCE_NAME, getUID(customResource));

    verify(eventSource, times(1))
        .eventSourceDeRegisteredForResource(eq(KubernetesResourceUtils.getUID(customResource)));
  }
}
