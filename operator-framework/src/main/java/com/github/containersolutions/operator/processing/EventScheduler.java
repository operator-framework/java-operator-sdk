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

    private final EventStore eventStore = new EventStore();
    private final ResourceCache resourceCache = new ResourceCache();

    private final EventDispatcher eventDispatcher;
    private final ScheduledThreadPoolExecutor executor;
    private final Retry retry;

    private final ReentrantLock lock = new ReentrantLock();

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
        resourceCache.cacheResource(resource); // always store the latest event. Outside the sync block is intentional.
        scheduleEventFromApi(new CustomResourceEvent(action, resource, retry));
    }

    void scheduleEventFromApi(CustomResourceEvent event) {
        try {
            lock.lock();
            if (event.getAction() == Action.DELETED) {
                // todo cancel retries and reprocessing
                log.debug("Skipping delete event for event: {}", event);
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
            scheduleEventForExecution(event);
            log.trace("Scheduling event from API finished: {}", event);
        } finally {
            lock.unlock();
        }
    }

    private void scheduleEventForExecution(CustomResourceEvent event) {
        try {
            lock.lock();
            log.trace("Current queue size {}", executor.getQueue().size());
            log.debug("Scheduling event for execution: {}", event);
            eventStore.addEventUnderProcessing(event);
            executor.execute(new EventConsumer(event, eventDispatcher, this));
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFinished(CustomResourceEvent event, DispatchControl dispatchControl) {
        try {
            // todo log debug messages
            lock.lock();
            eventStore.removeEventUnderProcessing(event.resourceUid());
            if (eventStore.containsNotScheduledEvent(event.resourceUid())) {
                scheduleNotYetScheduledEventForExecution(event.resourceUid());
            } else {
                // todo reprocess even is there is an event scheduled?
                if (dispatchControl.reprocessEvent()) {
                    scheduleEventForReprocessing(event);
                }
                if (dispatchControl.isError()) {
                    scheduleEventForRetry(event);
                }
            }
        } finally {
            lock.unlock();
        }
    }


    private void scheduleEventForReprocessing(CustomResourceEvent event) {

    }

    private void scheduleEventForRetry(CustomResourceEvent event) {

    }

    private class ReprocessSupport implements Runnable {

        @Override
        public void run() {

        }
    }

    private class RetrySupport implements Runnable {

        @Override
        public void run() {

        }
    }

    private void scheduleNotYetScheduledEventForExecution(String uuid) {
        CustomResourceEvent notScheduledEvent = eventStore.removeEventNotScheduled(uuid);
        scheduleEventForExecution(notScheduledEvent);
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


