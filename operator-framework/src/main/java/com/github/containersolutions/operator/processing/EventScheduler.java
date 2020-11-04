package com.github.containersolutions.operator.processing;


import com.github.containersolutions.operator.processing.event.*;
import com.github.containersolutions.operator.processing.event.internal.DelayedEventSource;
import com.github.containersolutions.operator.processing.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.containersolutions.operator.processing.ProcessingUtils.containsDeletedEvent;

/**
 * Requirements:
 * <ul>
 *   <li>Only 1 event should be processed at a time for same custom resource
 *   (metadata.name is the id, but kind and api should be taken into account)</li>
 *   <li>If event processing fails it should be rescheduled with retry - with limited number of retried
 *   and exponential time slacks (pluggable reschedule strategy in future?)</li>
 *   <li>if there are multiple events received for the same resource process only the last one. (Others can be discarded)
 *   User resourceVersion to check which is the latest. Put the new one at the and of the queue?
 *   </li>
 *   <li>Done - Avoid starvation, so on retry put back resource at the end of the queue.</li>
 *   <li>The selecting event from a queue should not be naive. So for example:
 *     If we cannot pick the last event because an event for that resource is currently processing just gor for the next one.
 *   </li>
 *   <li>Threading approach thus thread pool size and/or implementation should be configurable</li>
 * </ul>
 * <p>
 */

public class EventScheduler implements EventHandler {

    private final static Logger log = LoggerFactory.getLogger(EventScheduler.class);

    private final ResourceCache resourceCache;
    private final EventBuffer eventBuffer;
    private final Set<String> underProcessing = new HashSet<>();
    private final ScheduledThreadPoolExecutor executor;
    private final EventDispatcher eventDispatcher;
    private DefaultEventSourceManager defaultEventSourceManager;
    // todo remove?
    private final Retry retry;

    private final ReentrantLock lock = new ReentrantLock();

    public EventScheduler(ResourceCache resourceCache, EventDispatcher eventDispatcher, Retry retry) {
        this.resourceCache = resourceCache;
        this.eventDispatcher = eventDispatcher;
        this.retry = retry;
        eventBuffer = new EventBuffer();
        executor = new ScheduledThreadPoolExecutor(5);
    }

    public void setDefaultEventSourceManager(DefaultEventSourceManager defaultEventSourceManager) {
        this.defaultEventSourceManager = defaultEventSourceManager;
    }

    @Override
    public void handleEvent(Event event) {
        try {
            lock.lock();
            log.debug("Received event: {}", event);
            eventBuffer.addEvent(event);
            executeBufferedEvents(event.getRelatedCustomResourceUid());
        } finally {
            lock.unlock();
        }
    }

    private void executeBufferedEvents(String customResourceUid) {
        if (!isControllerUnderExecution(customResourceUid)
                && eventBuffer.containsEvents(customResourceUid)) {
            setUnderExecutionProcessing(customResourceUid);
            ExecutionScope executionScope =
                    new ExecutionScope(eventBuffer.getAndRemoveEventsForExecution(customResourceUid),
                            resourceCache.getLatestResource(customResourceUid).get());
            ExecutionConsumer executionConsumer =
                    new ExecutionConsumer(executionScope, eventDispatcher, this);
            executor.execute(executionConsumer);
        }
    }

    void eventProcessingFinished(ExecutionScope executionScope, PostExecutionControl postExecutionControl) {
        try {
            lock.lock();
            unsetUnderExecution(executionScope.getCustomResourceUid());
            // todo on retry error put back the events on the beginning of buffer list

            handleEventSourceRegistration(executionScope, postExecutionControl);
            defaultEventSourceManager.controllerExecuted(
                    new ExecutionDescriptor(executionScope, postExecutionControl, LocalDateTime.now()));

            if (containsDeletedEvent(executionScope.getEvents())) {
                cleanupAfterDeletedEvent(executionScope.getCustomResourceUid());
            } else {
                executeBufferedEvents(executionScope.getCustomResourceUid());
            }
        } finally {
            lock.unlock();
        }
    }

    private void handleEventSourceRegistration(ExecutionScope executionScope, PostExecutionControl postExecutionControl) {
        if (postExecutionControl.reprocessEvent()) {
            DelayedEventSource delayedEventSource = defaultEventSourceManager.registerEventSourceIfNotRegistered(
                    executionScope.getCustomResource(), DelayedEventSource.DEFAULT_NAME,
                    defaultEventSourceManager.getDelayedReprocessEventSource());
            delayedEventSource.scheduleDelayedEvent(executionScope.getCustomResourceUid(),
                    postExecutionControl.getReprocessDelay());
        }
    }

    private void cleanupAfterDeletedEvent(String customResourceUid) {
        defaultEventSourceManager.cleanup(customResourceUid);
        eventBuffer.cleanup(customResourceUid);
        resourceCache.cleanup(customResourceUid);
    }


    private boolean isControllerUnderExecution(String customResourceUid) {
        return underProcessing.contains(customResourceUid);
    }

    private void setUnderExecutionProcessing(String customResourceUid) {
        underProcessing.add(customResourceUid);
    }

    private void unsetUnderExecution(String customResourceUid) {
        underProcessing.remove(customResourceUid);
    }
}


