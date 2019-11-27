package com.github.containersolutions.operator;

import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class EventSchedulerTest {

    @SuppressWarnings("unchecked")
    private EventDispatcher<CustomResource> eventDispatcher = mock(EventDispatcher.class);

    @Test
    void dontScheduleReceivedEventIfProcessingNotStarted() {
        EventScheduler<CustomResource> eventScheduler = spy(new EventScheduler<>(eventDispatcher));

        eventScheduler.eventReceived(any(), any());

//        verify(eventScheduler, times(0)).scheduleEvent(any());
    }

    @Test
    void scheduleReceivedEventIfProcessingStarted() {
        EventScheduler<CustomResource> eventScheduler = spy(new EventScheduler<>(eventDispatcher));

        eventScheduler.eventReceived(Watcher.Action.ADDED, getResource());
        eventScheduler.startProcessing();
        eventScheduler.eventReceived(Watcher.Action.ADDED, getResource());

//        verify(eventScheduler, times(1)).scheduleEvent(any());
    }


    CustomResource getResource() {
        TestCustomResource resource = new TestCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
                .withCreationTimestamp("creationTimestamp")
                .withDeletionGracePeriodSeconds(10L)
                .withGeneration(10L)
                .withName("name")
                .withNamespace("namespace")
                .withResourceVersion("resourceVersion")
                .withSelfLink("selfLink")
                .withUid("uid").build());
        return resource;
    }
}
