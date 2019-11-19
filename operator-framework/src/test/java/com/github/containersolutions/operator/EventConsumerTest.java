package com.github.containersolutions.operator;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventConsumerTest {

    private EventDispatcher eventDispatcher = mock(EventDispatcher.class);
    private EventScheduler eventScheduler = mock(EventScheduler.class);
    private CustomResourceEvent customResourceEvent = mock(CustomResourceEvent.class);

    @Test
    void noRetryOnSuccess() {

        EventConsumer eventConsumer = new EventConsumer(customResourceEvent, eventDispatcher, eventScheduler);

        eventConsumer.run();

        verify(eventDispatcher, times(1)).handleEvent(any(), any());
        verify(eventScheduler, times(0)).eventProcessingFailed(customResourceEvent);
        verify(customResourceEvent, times(1)).getResource();
        verify(customResourceEvent, times(1)).getAction();

    }

    @Test
    void retryOnFailure() {

        EventConsumer eventConsumer = new EventConsumer(customResourceEvent, eventDispatcher, eventScheduler);

        doThrow(new RuntimeException("Processing event failed.")).when(eventDispatcher).handleEvent(any(), any());

        eventConsumer.run();

        verify(eventDispatcher, times(1)).handleEvent(any(), any());
        verify(eventScheduler, times(1)).eventProcessingFailed(customResourceEvent);
        verify(customResourceEvent, times(1)).getResource();
        verify(customResourceEvent, times(1)).getAction();

    }
}
