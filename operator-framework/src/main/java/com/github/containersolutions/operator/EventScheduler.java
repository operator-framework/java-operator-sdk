package com.github.containersolutions.operator;


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
import java.util.concurrent.*;
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

    // todo this if this is 0 its just works? We want also limit the number of retries etc
    private final static ExponentialBackOff backOff = new ExponentialBackOff(0L, 1.5);

    private final static Logger log = LoggerFactory.getLogger(EventScheduler.class);
    private final EventDispatcher eventDispatcher;
    private final ScheduledThreadPoolExecutor executor;
    private final HashMap<CustomResourceEvent, BackOffExecution> backoffSchedulerCache = new HashMap<>();
    private final Map<CustomResourceEvent, ScheduledFuture<?>> eventCache = new ConcurrentHashMap<>();
    private AtomicBoolean processingEnabled = new AtomicBoolean(false);
    private ReentrantLock lock = new ReentrantLock();

    EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("event-consumer-%d")
                .setDaemon(false)
                .build();
        executor = new ScheduledThreadPoolExecutor(1, threadFactory);
        executor.setRemoveOnCancelPolicy(true);
    }

    void startProcessing() {
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
    void scheduleEvent(CustomResourceEvent newEvent) {
        log.debug("Current queue size {}", executor.getQueue().size());
        log.info("Scheduling event: {}", newEvent.getEventInfo());
        try {
            // we have to lock since the fabric8 client event handling is multi-threaded,
            // so in the following part could be a race condition when multiple events are received for same resource.
            lock.lock();
            AtomicBoolean scheduleEvent = new AtomicBoolean(true);
            eventCache
                    .entrySet()
                    .parallelStream()
                    .forEach(entry -> {
                        CustomResourceEvent queuedEvent = entry.getKey();
                        ScheduledFuture<?> scheduledFuture = entry.getValue();
                        // Cleaning cache
                        if (scheduledFuture.isDone() || scheduledFuture.isCancelled()) {
                            log.debug("Event dropped from cache because is done or cancelled. [{}]", queuedEvent.getEventInfo());
                            eventCache.remove(queuedEvent, scheduledFuture);
                        }
                        // If newEvent is newer than existing in queue, cancel and remove queuedEvent
                        if (newEvent.isSameResourceAndNewerGeneration(queuedEvent)) {
                            log.debug("Queued event canceled because incoming event is newer. [{}]", queuedEvent.getEventInfo());
                            scheduledFuture.cancel(false);
                            eventCache.remove(queuedEvent, scheduledFuture);
                        }
                        // If newEvent is older than existing in queue, don't schedule and remove from cache
                        if (queuedEvent.isSameResourceAndNewerGeneration(newEvent)) {
                            log.debug("Incoming event canceled because queued event is newer. [{}]", newEvent.getEventInfo());
                            // todo this is not in cache at this point, or? (ask Marek)
                            eventCache.remove(newEvent);
                            scheduleEvent.set(false);
                        }
                    });
            if (!scheduleEvent.get()) return;
            backoffSchedulerCache.put(newEvent, backOff.start());
            ScheduledFuture<?> scheduledTask = executor.schedule(new EventConsumer(newEvent, eventDispatcher, this),
                    backoffSchedulerCache.get(newEvent).nextBackOff(), TimeUnit.MILLISECONDS);
            eventCache.put(newEvent, scheduledTask);
        } finally {
            lock.unlock();
        }
    }

    void retryFailedEvent(CustomResourceEvent event) {
        scheduleEvent(event);
    }

    @Override
    public void onClose(KubernetesClientException e) {
        processingEnabled.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            log.error("It was not possible to finish all threads, Killed them.");
        }
    }

}


