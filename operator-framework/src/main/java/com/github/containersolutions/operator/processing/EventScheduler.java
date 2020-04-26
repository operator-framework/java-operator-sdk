package com.github.containersolutions.operator.processing;


import com.github.containersolutions.operator.processing.retry.Retry;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

public class EventScheduler implements Watcher<CustomResource> {

    private final static Logger log = LoggerFactory.getLogger(EventScheduler.class);

    private final EventDispatcher eventDispatcher;
    private final ScheduledThreadPoolExecutor executor;
    private final EventStore eventStore = new EventStore();
    private final Retry retry;

    private ReentrantLock lock = new ReentrantLock();

    public EventScheduler(EventDispatcher eventDispatcher, Retry retry) {
        this.eventDispatcher = eventDispatcher;
        this.retry = retry;
        executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void eventReceived(Watcher.Action action, CustomResource resource) {
        log.debug("Event received for action: {}, {}: {}", action.toString().toLowerCase(), resource.getClass().getSimpleName(),
                resource.getMetadata().getName());
        CustomResourceEvent event = new CustomResourceEvent(action, resource, retry);
        scheduleEvent(event);
    }

    void scheduleEvent(CustomResourceEvent event) {
        log.trace("Current queue size {}", executor.getQueue().size());
        log.debug("Scheduling event: {}", event);
        try {
            lock.lock();
            if (event.getResource().getMetadata().getDeletionTimestamp() != null && event.getAction() == Action.DELETED) {
                // Note that we always use finalizers, we want to process delete event just in corner case,
                // when we are not able to add finalizer (lets say because of optimistic locking error, and the resource was deleted instantly).
                // We want to skip in case of finalizer was there since we don't want to execute delete method always at least 2x,
                // which would be the result if we don't skip here. (there is no deletion timestamp if resource deleted without finalizer.)
                log.debug("Skipping delete event since deletion timestamp is present on resource, so finalizer was in place.");
                return;
            }
            if (eventStore.containsNotScheduledEvent(event.resourceUid())) {
                log.debug("Replacing not scheduled event with actual event." +
                        " New event: {}", event);
                eventStore.addOrReplaceEventAsNotScheduled(event);
                return;
            }
            if (eventStore.containsEventUnderProcessing(event.resourceUid())) {
                log.debug("Scheduling event for later processing since there is an event under processing for same kind." +
                        " New event: {}", event);
                eventStore.addOrReplaceEventAsNotScheduled(event);
                return;
            }

            Optional<Long> nextBackOff = event.nextBackOff();
            if (!nextBackOff.isPresent()) {
                log.warn("Event max retry limit reached. Will be discarded. {}", event);
                return;
            }
            log.debug("Creating scheduled task for event: {}", event);
            eventStore.addEventUnderProcessing(event);
            executor.schedule(new EventConsumer(event, eventDispatcher, this),
                    nextBackOff.get(), TimeUnit.MILLISECONDS);
        } finally {
            log.debug("Scheduling event finished: {}", event);
            lock.unlock();
        }
    }

    void eventProcessingFinishedSuccessfully(CustomResourceEvent event) {
        try {
            lock.lock();
            log.debug("Event processing successful for event: {}", event);
            eventStore.removeEventUnderProcessing(event.resourceUid());
            if (eventStore.containsNotScheduledEvent(event.resourceUid())) {
                log.debug("Scheduling recent event for processing processing: {}", event);
                scheduleEvent(eventStore.removeEventNotScheduled(event.resourceUid()));
            }
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFailed(CustomResourceEvent event) {
        try {
            lock.lock();
            eventStore.removeEventUnderProcessing(event.resourceUid());
            if (eventStore.containsNotScheduledEvent(event.resourceUid())) {
                CustomResourceEvent notScheduledEvent = eventStore.removeEventNotScheduled(event.resourceUid());
                log.debug("Event processing failed. Scheduling the most recent event. Failed event: {}," +
                        " Most recent event: {}", event, notScheduledEvent);
                scheduleEvent(notScheduledEvent);
            } else {
                log.debug("Event processing failed. Attempting to re-schedule the event: {}", event);
                scheduleEvent(event);
            }
        } finally {
            lock.unlock();
        }
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


