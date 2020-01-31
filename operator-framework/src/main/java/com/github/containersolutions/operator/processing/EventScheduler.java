package com.github.containersolutions.operator.processing;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.containersolutions.operator.processing.CustomResourceEvent.MAX_RETRY_COUNT;


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
 * Notes:
 * <ul>
 *     <li> In implementation we have to lock since the fabric8 client event handling is multi-threaded, we can receive multiple events
 *          for same resource. Also we do callback from other threads.
 *   </li>
 *   <li>We don't react for delete event, since we always use finalizers and do delete on marked for deletion.</li>
 * </ul>
 *
 * @param <R>
 */

public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    private final static Logger log = LoggerFactory.getLogger(EventScheduler.class);

    private final EventDispatcher eventDispatcher;
    private final ScheduledThreadPoolExecutor executor;
    private final EventStore eventStore = new EventStore();

    private ReentrantLock lock = new ReentrantLock();

    public EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("event-consumer-%d")
                .setDaemon(false)
                .build();
        executor = new ScheduledThreadPoolExecutor(1, threadFactory);
        executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        log.debug("Event received for action: {}, {}: {}", action.toString().toLowerCase(), resource.getClass().getSimpleName(),
                resource.getMetadata().getName());
        CustomResourceEvent event = new CustomResourceEvent(action, resource);
        scheduleEvent(event);
    }

    void scheduleEvent(CustomResourceEvent event) {
        log.debug("Current queue size {}", executor.getQueue().size());
        log.info("Scheduling event: {}", event);
        try {
            lock.lock();
            if (event.getAction() == Action.DELETED) {
                log.debug("Received delete action for event: {}", event);
                //
                // should we still check if its a retry for a "marked for deletion" modification event?
                return;
            }
            if (eventStore.processedNewerVersionBefore(event)) {
                log.debug("Skipping event processing since was processed event with newer version before. {}", event);
                return;
            }
            eventStore.updateLatestResourceVersionProcessed(event);
            if (eventStore.containsOlderVersionOfNotScheduledEvent(event)) {
                log.debug("Replacing event which is not scheduled yet, since incoming event is more recent. new Event:{}", event);
                eventStore.addOrReplaceEventAsNotScheduledYet(event);
                return;
            }
            if (eventStore.containsOlderVersionOfEventUnderProcessing(event)) {
                log.debug("Scheduling event for later processing since there is an event under processing for same kind." +
                        " New event: {}", event);
                eventStore.addOrReplaceEventAsNotScheduledYet(event);
                return;
            }
            if (eventStore.containsEventScheduledForProcessing(event.resourceUid())) {
                EventStore.ResourceScheduleHolder scheduleHolder = eventStore.getEventScheduledForProcessing(event.resourceUid());
                CustomResourceEvent scheduledEvent = scheduleHolder.getCustomResourceEvent();
                ScheduledFuture<?> scheduledFuture = scheduleHolder.getScheduledFuture();
                // If newEvent is newer than existing in queue, cancel and remove queuedEvent
                if (event.isSameResourceAndNewerVersion(scheduledEvent)) {
                    log.debug("Scheduled event canceled because incoming event is newer. Discarded: {}, New: {}", scheduledEvent, event);
                    scheduledFuture.cancel(false);
                    eventStore.removeEventScheduledForProcessingVersionAware(scheduledEvent.resourceUid());
                }
            }

            Optional<Long> nextBackOff = event.nextBackOff();
            if (!nextBackOff.isPresent()) {
                log.warn("Event limited max retry limit ({}), will be discarded. {}", MAX_RETRY_COUNT, event);
                return;
            }
            log.debug("Creating scheduled task for event: {}", event);
            ScheduledFuture<?> scheduledTask = executor.schedule(new EventConsumer(event, eventDispatcher, this),
                    nextBackOff.get(), TimeUnit.MILLISECONDS);
            eventStore.addEventScheduledForProcessing(new EventStore.ResourceScheduleHolder(event, scheduledTask));
        } finally {
            log.info("Scheduling event finished: {}", event);
            lock.unlock();
        }
    }

    boolean eventProcessingStarted(CustomResourceEvent event) {
        try {
            lock.lock();
            EventStore.ResourceScheduleHolder res = eventStore.removeEventScheduledForProcessingVersionAware(event);
            if (res == null) {
                // Double checking if the event is still scheduled. This is a corner case, but can actually happen.
                // In detail: it can happen that we scheduled an event for processing, it took some time that is was picked
                // by executor, and it was removed during that time from the schedule but not cancelled yet. So to be correct
                // this should be checked also here. In other word scheduleEvent function can run in parallel with eventDispatcher.
                return false;
            }
            eventStore.addEventUnderProcessing(event);
            return true;
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFinishedSuccessfully(CustomResourceEvent event) {
        try {
            lock.lock();
            eventStore.removeEventUnderProcessing(event.resourceUid());
            CustomResourceEvent notScheduledYetEvent = eventStore.removeEventNotScheduledYet(event.resourceUid());
            if (notScheduledYetEvent != null) {
                scheduleEvent(notScheduledYetEvent);
            }
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFailed(CustomResourceEvent event) {
        try {
            lock.lock();
            eventStore.removeEventUnderProcessing(event.resourceUid());
            CustomResourceEvent notScheduledYetEvent = eventStore.removeEventNotScheduledYet(event.resourceUid());
            if (notScheduledYetEvent != null) {
                if (!notScheduledYetEvent.isSameResourceAndNewerVersion(event)) {
                    log.warn("The not yet scheduled event has older version then actual event. This is probably a bug.");
                }
                // this is the case when we failed processing an event but we already received a new one.
                // Since since we process declarative resources it correct to schedule the new event.
                scheduleEvent(notScheduledYetEvent);
            } else {
                scheduleEvent(event);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onClose(KubernetesClientException e) {
        if (e != null) {
            log.error("Error: ", e);
            // we will exit the application if there was a watching exception, because of the bug in fabric8 client
            // see https://github.com/fabric8io/kubernetes-client/issues/1318
            System.exit(1);
        }
    }
}


