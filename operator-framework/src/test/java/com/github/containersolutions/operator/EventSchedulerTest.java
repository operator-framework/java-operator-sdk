package com.github.containersolutions.operator;

import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class EventSchedulerTest {

    @SuppressWarnings("unchecked")
    private EventDispatcher<CustomResource> eventDispatcher = mock(EventDispatcher.class);

    private EventScheduler<CustomResource> eventScheduler = new EventScheduler(eventDispatcher);

    @Test
    public void schedulesEvent() {
        CustomResource resource = sampleResource();

        eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource);

        waitMinimalTimeForExecution();
        verify(eventDispatcher, times(1)).handleEvent(Watcher.Action.MODIFIED, resource);
    }

    @Test
    public void eventsAreNotExecutedConcurrentlyForSameResource() {

    }

    private void waitMinimalTimeForExecution() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    CustomResource sampleResource() {
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
