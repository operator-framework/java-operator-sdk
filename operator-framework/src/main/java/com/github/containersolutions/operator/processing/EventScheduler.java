package com.github.containersolutions.operator.processing;


import com.github.containersolutions.operator.processing.event.CustomResourceEvent;
import com.github.containersolutions.operator.processing.event.Event;
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

import static com.github.containersolutions.operator.processing.ProcessingUtils.*;

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

    //    private final EventStore eventStore = new EventStore();
    private final ResourceCache resourceCache = new ResourceCache();
    private final Set<String> underProcessing = new HashSet<>();
    private final EventBuffer eventBuffer;

    private final EventDispatcher eventDispatcher;
    private final Retry retry;


    private final ReentrantLock lock = new ReentrantLock();

    public EventScheduler(EventDispatcher eventDispatcher, Retry retry) {
        this.eventDispatcher = eventDispatcher;
        this.retry = retry;
        eventBuffer = new EventBuffer();
    }

    @Override
    public void eventReceived(Watcher.Action action, CustomResource resource) {
        log.debug("Event received for action: {}, {}: {}", action.toString().toLowerCase(), resource.getClass().getSimpleName(),
                resource.getMetadata().getName());
        resourceCache.cacheResource(resource); // always store the latest event. Outside the sync block is intentional.
        if (action == Action.DELETED) {
            // todo cleanup
            log.debug("Skipping delete event for custom resource: {}", resource);
            return;
        }
        scheduleEvent(new CustomResourceEvent(action, resource, retry));
    }

    public void scheduleEvent(Event event) {
        try {
            lock.lock();
            if (event instanceof CustomResourceEvent) {
                eventBuffer.addOrUpdateLatestCustomResourceEvent((CustomResourceEvent) event);
            } else {
                eventBuffer.addEvent(event);
            }
            if (!isControllerUnderExecutionForCR(event.getRelatedCustomResourceUid())) {
                executeEvents(event.getRelatedCustomResourceUid());
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean isControllerUnderExecutionForCR(String customResource) {
        return underProcessing.contains(customResource);
    }


    private void executeEvents(String customResourceUid) {
        try {
            lock.lock();
            underProcessing.add(customResourceUid);
//            executor.execute(new ExecutionUnit(event, eventDispatcher, this));
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFinished(ExecutionUnit event, DispatchControl dispatchControl) {
        try {
            // todo log debug messages
//            lock.lock();
//            eventStore.removeEventUnderProcessing(event.resourceUid());
//            if (eventStore.containsNotScheduledEvent(event.resourceUid())) {
//                scheduleNotYetScheduledEventForExecution(event.resourceUid());
//            } else {
//                // todo reprocess even is there is an event scheduled?
//                if (dispatchControl.reprocessEvent()) {
//                    scheduleEventForReprocessing(event);
//                }
//                if (dispatchControl.isError()) {
//                    scheduleEventForRetry(event);
//                }
//            }
        } finally {
            lock.unlock();
        }
    }


    private void scheduleEventForReprocessing(CustomResourceEvent event) {

    }

    private void scheduleEventForRetry(CustomResourceEvent event) {

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


