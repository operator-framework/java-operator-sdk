package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.KubernetesResourceUtils;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TimerEventSourceTest {

    public static final int INITIAL_DELAY = 50;
    public static final int PERIOD = 50;
    public static final int TESTING_TIME_SLACK = 20;

    private TimerEventSource timerEventSource;
    private EventHandler eventHandlerMock = mock(EventHandler.class);
    private EventSourceManager eventSourceManagerMock = mock(EventSourceManager.class);

    @BeforeEach
    public void setup() {
        timerEventSource = new TimerEventSource();
        timerEventSource.setEventHandler(eventHandlerMock);
        timerEventSource.setEventSourceManager(eventSourceManagerMock);
    }

    @Test
    public void producesOneTimeEvent() {
        CustomResource customResource = TestUtils.testCustomResource();

        timerEventSource.scheduleOnce(customResource, INITIAL_DELAY);

        ArgumentCaptor<TimerEvent> argumentCaptor = ArgumentCaptor.forClass(TimerEvent.class);
        verify(eventHandlerMock, timeout(100).times(1)).handleEvent(argumentCaptor.capture());
        TimerEvent event = argumentCaptor.getValue();
        assertThat(event.getRelatedCustomResourceUid()).isEqualTo(getUID(customResource));
        assertThat(event.getEventSource()).isEqualTo(timerEventSource);
    }

    @Test
    public void producesEventsPeriodically() {
        CustomResource customResource = TestUtils.testCustomResource();

        timerEventSource.schedule(customResource, INITIAL_DELAY, PERIOD);

        ArgumentCaptor<TimerEvent> argumentCaptor = ArgumentCaptor.forClass(TimerEvent.class);
        verify(eventHandlerMock, timeout(INITIAL_DELAY + PERIOD + TESTING_TIME_SLACK).times(2))
                .handleEvent(argumentCaptor.capture());
        List<TimerEvent> events = argumentCaptor.getAllValues();
        assertThat(events).allMatch(e -> e.getRelatedCustomResourceUid().equals(getUID(customResource)));
        assertThat(events).allMatch(e -> e.getEventSource().equals(timerEventSource));
    }

    @Test
    public void deRegistersPeriodicalEventSources() throws InterruptedException {
        CustomResource customResource = TestUtils.testCustomResource();

        timerEventSource.schedule(customResource, INITIAL_DELAY, PERIOD);
        Thread.sleep(INITIAL_DELAY + PERIOD + TESTING_TIME_SLACK);
        timerEventSource.eventSourceDeRegisteredForResource(getUID(customResource));
        Thread.sleep(PERIOD + TESTING_TIME_SLACK);

        verify(eventHandlerMock, times(2))
                .handleEvent(any());
    }

    @Test
    public void deRegistersOneTimeEventSource() throws InterruptedException {
        CustomResource customResource = TestUtils.testCustomResource();

        timerEventSource.scheduleOnce(customResource, INITIAL_DELAY);
        Thread.sleep(TESTING_TIME_SLACK);
        timerEventSource.eventSourceDeRegisteredForResource(getUID(customResource));
        Thread.sleep(PERIOD + TESTING_TIME_SLACK);

        verify(eventHandlerMock, times(0))
                .handleEvent(any());
    }

}