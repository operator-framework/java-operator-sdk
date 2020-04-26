package com.github.containersolutions.operator;

import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.processing.retry.GenericRetry;
import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.containersolutions.operator.processing.retry.GenericRetry.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Mockito.*;

class EventSchedulerTest {

    public static final int INVOCATION_DURATION = 80;
    @SuppressWarnings("unchecked")
    private EventDispatcher eventDispatcher = mock(EventDispatcher.class);

    private EventScheduler eventScheduler = new EventScheduler(eventDispatcher, GenericRetry.defaultLimitedExponentialRetry());

    private List<EventProcessingDetail> eventProcessingList = Collections.synchronizedList(new ArrayList<>());


    @Test
    public void schedulesEvent() {
        normalDispatcherExecution();
        CustomResource resource = sampleResource();

        eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource);

        waitMinimalTimeForExecution();
        verify(eventDispatcher, times(1)).handleEvent(Watcher.Action.MODIFIED, resource);
        assertThat(eventProcessingList).hasSize(1);
    }

    @Test
    public void eventsAreNotExecutedConcurrentlyForSameResource() throws InterruptedException {
        normalDispatcherExecution();
        CustomResource resource1 = sampleResource();
        CustomResource resource2 = sampleResource();
        resource2.getMetadata().setResourceVersion("2");

        CompletableFuture.runAsync(() -> eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource1));
        Thread.sleep(50);
        CompletableFuture.runAsync(() -> eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource2));

        waitTimeForExecution(2);
        assertThat(eventProcessingList).hasSize(2)
                .matches(list -> eventProcessingList.get(0).getCustomResource().getMetadata().getResourceVersion().equals("1") &&
                                eventProcessingList.get(1).getCustomResource().getMetadata().getResourceVersion().equals("2"),
                        "Events processed in correct order")
                .matches(list ->
                                eventProcessingList.get(0).getEndTime().isBefore(eventProcessingList.get(1).startTime),
                        "Start time of event 2 is after end time of event 1");
    }

    @Test
    public void retriesEventsWithErrors() {
        doAnswer(this::exceptionInExecution)
                .doAnswer(this::normalExecution)
                .when(eventDispatcher)
                .handleEvent(any(Watcher.Action.class), any(CustomResource.class));

        CustomResource resource = sampleResource();

        eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource);
        waitTimeForExecution(2, 1);

        assertThat(eventProcessingList)
                .hasSize(2)
                .has(new Condition<>(e -> e.getException() != null, ""), atIndex(0))
                .has(new Condition<>(e -> e.getException() == null, ""), atIndex(1));
    }

    @Disabled("Todo change according to new scheduling")
    @Test
    public void schedulesEventIfOlderVersionIsAlreadyUnderProcessing() {
        normalDispatcherExecution();
        CustomResource resource1 = sampleResource();
        CustomResource resource2 = sampleResource();
        resource2.getMetadata().setResourceVersion("2");

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            LocalDateTime start = LocalDateTime.now();
            CompletableFuture.runAsync(() -> eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource2));
            Thread.sleep(INVOCATION_DURATION);
            LocalDateTime end = LocalDateTime.now();
            eventProcessingList.add(new EventProcessingDetail((Watcher.Action) args[0], start, end, (CustomResource) args[1]));
            return null;
        }).doAnswer(this::normalExecution).when(eventDispatcher).handleEvent(any(Watcher.Action.class), any(CustomResource.class));

        CompletableFuture.runAsync(() -> eventScheduler.eventReceived(Watcher.Action.MODIFIED, resource1));

        waitTimeForExecution(2);
        assertThat(eventProcessingList).hasSize(2)
                .matches(list -> eventProcessingList.get(0).getCustomResource().getMetadata().getResourceVersion().equals("1") &&
                                eventProcessingList.get(1).getCustomResource().getMetadata().getResourceVersion().equals("2"),
                        "Events processed in correct order")
                .matches(list ->
                                eventProcessingList.get(0).getEndTime().isBefore(eventProcessingList.get(1).startTime),
                        "Start time of event 2 is after end time of event 1");
    }

    @Test
    public void numberOfRetriesIsLimited() {
        doAnswer(this::exceptionInExecution).when(eventDispatcher).handleEvent(any(Watcher.Action.class), any(CustomResource.class));

        CompletableFuture.runAsync(() -> eventScheduler.eventReceived(Watcher.Action.MODIFIED, sampleResource()));

        waitTimeForExecution(1, DEFAULT_MAX_ATTEMPTS + 2);
        assertThat(eventProcessingList).hasSize(DEFAULT_MAX_ATTEMPTS);
    }

    public void normalDispatcherExecution() {
        doAnswer(this::normalExecution).when(eventDispatcher).handleEvent(any(Watcher.Action.class), any(CustomResource.class));
    }

    private Object normalExecution(InvocationOnMock invocation) {
        try {
            Object[] args = invocation.getArguments();
            LocalDateTime start = LocalDateTime.now();
            Thread.sleep(INVOCATION_DURATION);
            LocalDateTime end = LocalDateTime.now();
            eventProcessingList.add(new EventProcessingDetail((Watcher.Action) args[0], start, end, (CustomResource) args[1]));
            return null;
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }


    private Object exceptionInExecution(InvocationOnMock invocation) {
        try {
            Object[] args = invocation.getArguments();
            LocalDateTime start = LocalDateTime.now();
            Thread.sleep(INVOCATION_DURATION);
            LocalDateTime end = LocalDateTime.now();
            IllegalStateException exception = new IllegalStateException("Exception thrown for testing purposes");
            eventProcessingList.add(new EventProcessingDetail((Watcher.Action) args[0], start, end, (CustomResource) args[1], exception));
            throw exception;
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void waitMinimalTimeForExecution() {
        waitTimeForExecution(1);
    }

    private void waitTimeForExecution(int numberOfEvents) {
        waitTimeForExecution(numberOfEvents, 0);
    }

    private void waitTimeForExecution(int numberOfEvents, int retries) {
        try {
            Thread.sleep((long) (200 + ((INVOCATION_DURATION + 30) * numberOfEvents) + (retries * (INVOCATION_DURATION + 100)) +
                    (Math.pow(DEFAULT_MULTIPLIER, retries) * (DEFAULT_INITIAL_INTERVAL + 100))));
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
                .withResourceVersion("1")
                .withSelfLink("selfLink")
                .withUid("uid").build());
        return resource;
    }

    private static class EventProcessingDetail {
        private Watcher.Action action;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private CustomResource customResource;
        private Exception exception;

        public EventProcessingDetail(Watcher.Action action, LocalDateTime startTime, LocalDateTime endTime, CustomResource customResource, Exception exception) {
            this.action = action;
            this.startTime = startTime;
            this.endTime = endTime;
            this.customResource = customResource;
            this.exception = exception;
        }

        public EventProcessingDetail(Watcher.Action action, LocalDateTime startTime, LocalDateTime endTime,
                                     CustomResource customResource) {
            this(action, startTime, endTime, customResource, null);
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public EventProcessingDetail setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public EventProcessingDetail setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public CustomResource getCustomResource() {
            return customResource;
        }

        public EventProcessingDetail setCustomResource(CustomResource customResource) {
            this.customResource = customResource;
            return this;
        }

        public Watcher.Action getAction() {
            return action;
        }

        public EventProcessingDetail setAction(Watcher.Action action) {
            this.action = action;
            return this;
        }

        public Exception getException() {
            return exception;
        }
    }
}
