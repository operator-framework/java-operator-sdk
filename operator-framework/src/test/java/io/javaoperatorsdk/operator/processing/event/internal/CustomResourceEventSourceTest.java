package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.CustomResourceCache;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomResourceEventSourceTest {

    CustomResourceCache customResourceCache = new CustomResourceCache();
    MixedOperation mixedOperation = mock(MixedOperation.class);
    EventHandler eventHandler = mock(EventHandler.class);

    private CustomResourceEventSource customResourceEventSource =
            CustomResourceEventSource.customResourceEventSourceForAllNamespaces(customResourceCache, mixedOperation, true);

    @BeforeEach
    public void setup() {
        customResourceEventSource.setEventHandler(eventHandler);
    }

    @Test
    public void skipsEventHandlingIfGenerationNotIncreased() {
        TestCustomResource customResource1 = TestUtils.testCustomResource();

        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(1)).handleEvent(any());

        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(1)).handleEvent(any());
    }

    @Test
    public void dontSkipEventHandlingIfMarkedForDeletion() {
        TestCustomResource customResource1 = TestUtils.testCustomResource();

        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(1)).handleEvent(any());

        // mark for deletion
        customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(2)).handleEvent(any());
    }


    @Test
    public void normalExecutionIfGenerationChanges() {
        TestCustomResource customResource1 = TestUtils.testCustomResource();

        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(1)).handleEvent(any());

        customResource1.getMetadata().setGeneration(2L);
        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(2)).handleEvent(any());
    }

    @Test
    public void handlesAllEventIfNotGenerationAware() {
        customResourceEventSource =
                CustomResourceEventSource.customResourceEventSourceForAllNamespaces(customResourceCache, mixedOperation, false);
        setup();

        TestCustomResource customResource1 = TestUtils.testCustomResource();

        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(1)).handleEvent(any());

        customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
        verify(eventHandler,times(2)).handleEvent(any());
    }

}