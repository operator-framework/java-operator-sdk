package io.javaoperatorsdk.operator.processing;


import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ExecutionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

import static io.javaoperatorsdk.operator.processing.ProcessingUtils.containsCustomResourceDeletedEvent;

/**
 * Event handler that makes sure that events are processed in a "single threaded" way, while buffering
 * events which are received during an execution.
 */

public class DefaultEventHandler implements EventHandler {

    private final static Logger log = LoggerFactory.getLogger(DefaultEventHandler.class);

    private final ResourceCache resourceCache;
    private final EventBuffer eventBuffer;
    private final Set<String> underProcessing = new HashSet<>();
    private final ScheduledThreadPoolExecutor executor;
    private final EventDispatcher eventDispatcher;
    private DefaultEventSourceManager defaultEventSourceManager;

    private final ReentrantLock lock = new ReentrantLock();

    public DefaultEventHandler(ResourceCache resourceCache, EventDispatcher eventDispatcher) {
        this.resourceCache = resourceCache;
        this.eventDispatcher = eventDispatcher;
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
        if (!isControllerUnderExecution(customResourceUid) && eventBuffer.containsEvents(customResourceUid)) {
            setUnderExecutionProcessing(customResourceUid);
            ExecutionScope executionScope = new ExecutionScope(
                    eventBuffer.getAndRemoveEventsForExecution(customResourceUid),
                    resourceCache.getLatestResource(customResourceUid).get());
            executor.execute(new ExecutionConsumer(executionScope, eventDispatcher, this));
        }
    }

    void eventProcessingFinished(ExecutionScope executionScope, PostExecutionControl postExecutionControl) {
        try {
            lock.lock();
            unsetUnderExecution(executionScope.getCustomResourceUid());
            defaultEventSourceManager.controllerExecuted(
                    new ExecutionDescriptor(executionScope, postExecutionControl, LocalDateTime.now()));
            if (containsCustomResourceDeletedEvent(executionScope.getEvents())) {
                cleanupAfterDeletedEvent(executionScope.getCustomResourceUid());
            } else {
                executeBufferedEvents(executionScope.getCustomResourceUid());
            }
        } finally {
            lock.unlock();
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


