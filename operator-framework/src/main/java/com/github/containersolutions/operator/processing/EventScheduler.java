package com.github.containersolutions.operator.processing;


import com.github.containersolutions.operator.processing.event.*;
import com.github.containersolutions.operator.processing.event.AbstractEventSource;
import com.github.containersolutions.operator.processing.event.CustomResourceEvent;
import com.github.containersolutions.operator.processing.event.PreviousProcessingCompletedEvent;
import com.github.containersolutions.operator.processing.retry.Retry;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

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

public class EventScheduler extends AbstractEventSource {

    private final static Logger log = LoggerFactory.getLogger(EventScheduler.class);

    //    private final EventStore eventStore = new EventStore();
    private final ResourceCache resourceCache = new ResourceCache();
    private final Set<String> underProcessing = new HashSet<>();
    private final EventBuffer eventBuffer;
    private final ScheduledThreadPoolExecutor executor;
    private final EventDispatcher eventDispatcher;
    private final Retry retry;
    private final EventManager eventManager;

    private final ReentrantLock lock = new ReentrantLock();

    public EventScheduler(EventDispatcher eventDispatcher, Retry retry, EventManager eventManager) {
        this.eventDispatcher = eventDispatcher;
        this.retry = retry;
        this.eventManager = eventManager;
        eventBuffer = new EventBuffer();
        executor = new ScheduledThreadPoolExecutor(1);
    }

    @Override
    public void eventReceived(Watcher.Action action, CustomResource resource) {
        log.debug("Event received for action: {}, {}: {}", action.toString().toLowerCase(), resource.getClass().getSimpleName(),
                resource.getMetadata().getName());
        resourceCache.cacheResource(resource); // always store the latest event. Outside the sync block is intentional.
        // todo cleanup on delete?!
        if (action == Action.DELETED || action == Action.ERROR) {
            log.debug("Skipping {} event for custom resource: {}", action, resource);
            return;
        }
        scheduleEvent(new CustomResourceEvent(action, resource, retry));
    }

    public void scheduleEvent(Event event) {
        try {
            lock.lock();
            if (!(event instanceof PreviousProcessingCompletedEvent)) {
                eventBuffer.addEvent(event);
            }
            if (!isControllerUnderExecution(event.getRelatedCustomResourceUid())
                    && eventBuffer.containsEvents(event.getRelatedCustomResourceUid())) {
                executeEvents(event.getRelatedCustomResourceUid());
            }
        } finally {
            lock.unlock();
        }
    }


    private void executeEvents(String customResourceUid) {
        try {
            lock.lock();
            setUnderExecutionProcessing(customResourceUid);
            ExecutionScope executionScope =
                    new ExecutionScope(eventBuffer.getAndRemoveEventsForExecution(customResourceUid),
                            resourceCache.getLatestResource(customResourceUid).get());
            ExecutionConsumer executionConsumer =
                    new ExecutionConsumer(executionScope, eventDispatcher, this);
            executor.execute(executionConsumer);
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFinished(ExecutionScope executionScope, PostExecutionControl postExecutionControl) {
        try {
            lock.lock();
            unsetUnderExecution(executionScope.getCustomResourceUid());
            eventManager.eventProcessingFinished(new ExecutionDescriptor(executionScope, postExecutionControl));
        } finally {
            lock.unlock();
        }
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

    @Override
    public void onClose(KubernetesClientException e) {
        log.error("Error: ", e);
        // we will exit the application if there was a watching exception, because of the bug in fabric8 client
        // see https://github.com/fabric8io/kubernetes-client/issues/1318
        // Note that this should not happen normally, since fabric8 client handles reconnect.
        // In case it tries to reconnect this method is not called.
        System.exit(1);
    }
}


