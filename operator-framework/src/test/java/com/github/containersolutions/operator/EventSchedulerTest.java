package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.Initializers;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventSchedulerTest {

    @SuppressWarnings("unchecked")
    private EventDispatcher<CustomResource> eventDispatcher = mock(EventDispatcher.class);

    @Test
    void dontScheduleReceivedEventIfProcessingNotStarted() {
        EventScheduler<CustomResource> eventScheduler = spy(new EventScheduler<>(eventDispatcher));

        eventScheduler.eventReceived(any(), any());

        verify(eventScheduler, times(0)).scheduleEvent(any());
    }

    @Test
    void scheduleReceivedEventIfProcessingStarted() {
        EventScheduler<CustomResource> eventScheduler = spy(new EventScheduler<>(eventDispatcher));

        eventScheduler.eventReceived(Watcher.Action.ADDED, getResource());
        eventScheduler.startProcessing();
        eventScheduler.eventReceived(Watcher.Action.ADDED, getResource());

        verify(eventScheduler, times(1)).scheduleEvent(any());
    }


    CustomResource getResource() {
        TestCustomResource resource = new TestCustomResource();
        resource.setMetadata(new ObjectMeta(
                new HashMap<String, String>(),
                "clusterName",
                "creationTimestamp",
                10L,
                "deletionTimestamp",
                new LinkedList<String>(),
                "generatedName",
                10L,
                new Initializers(),
                new HashMap<String, String>(),
                "name",
                "namespace",
                new LinkedList<OwnerReference>(),
                "resourceVersion",
                "selfLink",
                "uid"
        ));
        return resource;
    }
}