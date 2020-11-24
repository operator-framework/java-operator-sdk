package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.javaoperatorsdk.operator.processing.ProcessingUtils.getUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

class DefaultEventSourceManagerTest {

    public static final String CUSTOM_EVENT_SOURCE_NAME = "CustomEventSource";

    private DefaultEventHandler defaultEventHandlerMock = mock(DefaultEventHandler.class);
    private DefaultEventSourceManager defaultEventSourceManager = new DefaultEventSourceManager(defaultEventHandlerMock);


    @Test
    public void registersEventSource() {
        CustomResource customResource = TestUtils.testCustomResource();
        EventSource eventSource = mock(EventSource.class);

        defaultEventSourceManager.registerEventSource(customResource, CUSTOM_EVENT_SOURCE_NAME, eventSource);

        Map<String, EventSource> registeredSources =
                defaultEventSourceManager.getRegisteredEventSources(getUID(customResource));
        assertThat(registeredSources.entrySet()).hasSize(1);
        assertThat(registeredSources.get(CUSTOM_EVENT_SOURCE_NAME)).isEqualTo(eventSource);
        verify(eventSource, times(1)).eventSourceRegisteredForResource(eq(customResource));
        verify(eventSource, times(1)).setEventHandler(eq(defaultEventHandlerMock));
    }

    @Test
    public void throwExceptionIfRegisteringEventSourceWithSameName() {
        CustomResource customResource = TestUtils.testCustomResource();
        EventSource eventSource = mock(EventSource.class);
        EventSource eventSource2 = mock(EventSource.class);

        defaultEventSourceManager.registerEventSource(customResource, CUSTOM_EVENT_SOURCE_NAME, eventSource);
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            defaultEventSourceManager.registerEventSource(customResource, CUSTOM_EVENT_SOURCE_NAME, eventSource2);
        });
    }

    @Test
    public void registersEventSourceOnlyIfNotRegistered() {
        CustomResource customResource = TestUtils.testCustomResource();
        EventSource eventSource = mock(EventSource.class);
        EventSource eventSource2 = mock(EventSource.class);

        defaultEventSourceManager.registerEventSourceIfNotRegistered(customResource, CUSTOM_EVENT_SOURCE_NAME, eventSource);
        defaultEventSourceManager.registerEventSourceIfNotRegistered(customResource, CUSTOM_EVENT_SOURCE_NAME, eventSource2);

        Map<String, EventSource> registeredEventSources = defaultEventSourceManager
                .getRegisteredEventSources(getUID(customResource));
        assertThat(registeredEventSources.get(CUSTOM_EVENT_SOURCE_NAME)).isEqualTo(eventSource);
    }

    @Test
    public void deRegistersEventSources() {
        CustomResource customResource = TestUtils.testCustomResource();
        EventSource eventSource = mock(EventSource.class);
        defaultEventSourceManager.registerEventSourceIfNotRegistered(customResource, CUSTOM_EVENT_SOURCE_NAME, eventSource);

        defaultEventSourceManager.deRegisterEventSource(getUID(customResource), CUSTOM_EVENT_SOURCE_NAME);

        assertThat(defaultEventSourceManager.getRegisteredEventSources(getUID(customResource))).isEmpty();
    }

}