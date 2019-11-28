package com.github.containersolutions.operator.processing;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Requirements:
 * <ul>
 *   <li>Only 1 event should be processed at a time for same custom resource
 *   (metadata.name is the id, but kind and api should be taken into account)</li>
 *   <li>Done - If event processing fails it should be rescheduled with retry - with limited number of retried
 *   and exponential time slacks (pluggable reschedule strategy in future?)</li>
 *   <li>if there are multiple events received for the same resource process only the last one. (Others can be discarded)
 *   User resourceVersion to check which is the latest. Put the new one at the and of the queue?
 *   </li>
 *   <li>Done - Avoid starvation, so on retry put back resource at the end of the queue.</li>
 *   <li>The selecting event from a queue should not be naive. So for example:
 *     If we cannot pick the last event because an event for that resource is currently processing just gor for the next one.
 *     (Maybe is good to represent this queue with a list.) Or if and event is rescheduled
 *     (skip if there is not enough time left since last execution)
 *   </li>
 *   <li>Impossible, scheduled chosen - Threading approach thus thread pool size and/or implementation should be configurable</li>
 *   <li>see also: https://github.com/ContainerSolutions/java-operator-sdk/issues/34</li>
 * </ul>
 *
 * @param <R>
 */


public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    // todo limit number of back offs
    private final static ExponentialBackOff backOff = new ExponentialBackOff(2000L, 1.5);

    private final static Logger log = LoggerFactory.getLogger(EventScheduler.class);
    private final EventDispatcher eventDispatcher;
    private final ScheduledThreadPoolExecutor executor;
    private final HashMap<CustomResourceEvent, BackOffExecution> backoffSchedulerCache = new HashMap<>();

    // todo check uid for key
    // note that these hash maps does not needs to be concurrent, since we are already locking all methods where are used
    private final Map<String, CustomResourceEvent> eventsNotScheduledYet = new HashMap<>();
    private final Map<String, ResourceScheduleHolder> eventsScheduledForProcessing = new HashMap<>();
    private final Map<String, CustomResourceEvent> eventsUnderProcessing = new HashMap<>();

    private AtomicBoolean processingEnabled = new AtomicBoolean(false);
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

    public void startProcessing() {
        processingEnabled.set(true);
    }

    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        if (!processingEnabled.get()) return;

        log.debug("Event received for action: {}, {}: {}", action.toString().toLowerCase(), resource.getClass().getSimpleName(),
                resource.getMetadata().getName());

        CustomResourceEvent event = new CustomResourceEvent(action, resource);
        scheduleEvent(event);
    }

    // todo we want to strictly scheduler resources for execution, so if an event is under processing we should wait
    //  with the incoming event to be scheduled/executed until that one is not finished

    // todo handle delete event: cleanup when a real delete arrived
    // todo discuss new version vs new generation comparison
    void scheduleEvent(CustomResourceEvent newEvent) {
        log.debug("Current queue size {}", executor.getQueue().size());
        log.info("Scheduling event: {}", newEvent.getEventInfo());
        try {
            // we have to lock since the fabric8 client event handling is multi-threaded,
            // so in the following part could be a race condition when multiple events are received for same resource.
            lock.lock();

            // if there is an event waiting for to be scheduled we just replace that.
            if (eventsNotScheduledYet.containsKey(newEvent.resourceKey()) &&
                    newEvent.isSameResourceAndNewerVersion(eventsNotScheduledYet.get(newEvent.resourceKey()))) {
                log.debug("Replacing event which is not scheduled yet, since incoming event is more recent. new Event:{}"
                        , newEvent);
                eventsNotScheduledYet.put(newEvent.resourceKey(), newEvent);
                return;
            } else if (eventsUnderProcessing.containsKey(newEvent.resourceKey()) &&
                    newEvent.isSameResourceAndNewerVersion(eventsUnderProcessing.get(newEvent.resourceKey()))) {
                log.debug("Scheduling event for later processing since there is an event under processing for same kind." +
                        " New event: {}", newEvent);
                eventsNotScheduledYet.put(newEvent.resourceKey(), newEvent);
                return;
            }

            if (eventsScheduledForProcessing.containsKey(newEvent.resourceKey())) {
                ResourceScheduleHolder scheduleHolder = eventsScheduledForProcessing.get(newEvent.resourceKey());
                CustomResourceEvent scheduledEvent = scheduleHolder.getCustomResourceEvent();
                ScheduledFuture<?> scheduledFuture = scheduleHolder.getScheduledFuture();
                // If newEvent is newer than existing in queue, cancel and remove queuedEvent
                if (newEvent.isSameResourceAndNewerVersion(scheduledEvent)) {
                    log.debug("Queued event canceled because incoming event is newer. [{}]", scheduledEvent);
                    scheduledFuture.cancel(false);
                    eventsScheduledForProcessing.remove(scheduledEvent.resourceKey());
                }
                // If newEvent is older than existing in queue, don't schedule and remove from cache
                if (scheduledEvent.isSameResourceAndNewerVersion(newEvent)) {
                    log.debug("Incoming event discarded because queued event is newer. [{}]", newEvent);
                    return;
                }
            }
            backoffSchedulerCache.put(newEvent, backOff.start());
            ScheduledFuture<?> scheduledTask = executor.schedule(new EventConsumer(newEvent, eventDispatcher, this),
                    backoffSchedulerCache.get(newEvent).nextBackOff(), TimeUnit.MILLISECONDS);
            eventsScheduledForProcessing.put(newEvent.resourceKey(), new ResourceScheduleHolder(newEvent, scheduledTask));
        } finally {
            lock.unlock();
        }
    }

    boolean eventProcessingStarted(CustomResourceEvent event) {
        try {
            lock.lock();
            ResourceScheduleHolder res = eventsScheduledForProcessing.remove(event.resourceKey());
            if (res == null) {
                // if its still scheduled for processing.
                // note that it can happen that we scheduled an event for processing, it took some time that is was picked
                // by executor, and it was removed during that time from the schedule but not cancelled yet. So to be correct
                // this should be checked also here. In other word scheduleEvent function can run in parallel with eventDispatcher.
                return false;
            }
            eventsUnderProcessing.put(event.resourceKey(), event);
            return true;
        } finally {
            lock.unlock();
        }
    }

    void eventProcessingFinishedSuccessfully(CustomResourceEvent event) {
        try {
            lock.lock();
            eventsUnderProcessing.remove(event.resourceKey());
            backoffSchedulerCache.remove(event);

            CustomResourceEvent notScheduledYetEvent = eventsNotScheduledYet.remove(event.resourceKey());
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
            eventsUnderProcessing.remove(event);
            scheduleEvent(event);
        } finally {
            lock.unlock();
        }
    }

    // todo review this in light of new restart functionality from master
    @Override
    public void onClose(KubernetesClientException e) {
//     todo re apply the watch
    }

    private static class ResourceScheduleHolder {
        private CustomResourceEvent customResourceEvent;
        private ScheduledFuture<?> scheduledFuture;

        public ResourceScheduleHolder(CustomResourceEvent customResourceEvent, ScheduledFuture<?> scheduledFuture) {
            this.customResourceEvent = customResourceEvent;
            this.scheduledFuture = scheduledFuture;
        }

        public CustomResourceEvent getCustomResourceEvent() {
            return customResourceEvent;
        }

        public ScheduledFuture<?> getScheduledFuture() {
            return scheduledFuture;
        }
    }
}


