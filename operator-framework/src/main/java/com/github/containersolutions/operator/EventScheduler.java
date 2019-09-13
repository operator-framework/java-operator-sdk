package com.github.containersolutions.operator;


import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 *   <li>Avoid starvation, so on retry put back resource at the end of the queue.</li>
 *   <li>The selecting event from a queue should not be naive. So for example:
 *     If we cannot pick the last event because an event for that resource is currently processing just gor for the next one.
 *     (Maybe is good to represent this queue with a list.) Or if and event is rescheduled
 *     (skip if there is not enough time left since last execution)
 *   </li>
 *   <li>Threading approach thus thread pool size and/or implementation should be configurable</li>
 *   <li>see also: https://github.com/ContainerSolutions/java-operator-sdk/issues/34</li>
 * </ul>
 *
 * @param <R>
 */

public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final Map<String, Pair<Action, CustomResource>> retryEventQueue = Collections.synchronizedMap(new HashMap<>());

    private final EventDispatcher eventDispatcher;

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

    private final int retryPeriodSeconds;

    public EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        this.retryPeriodSeconds = 5;
    }

    public EventScheduler(EventDispatcher<R> eventDispatcher, int retryPeriodSeconds) {
        this.eventDispatcher = eventDispatcher;
        this.retryPeriodSeconds = retryPeriodSeconds;
    }

    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        String resourceUid = resource.getMetadata().getUid();
        Pair<Action, CustomResource> event = new ImmutablePair<>(action, resource);
        try {
            log.debug("Event received for action: {}, {}: {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName());
            eventDispatcher.handleEvent(action, resource);
            log.info("Event handling finished for action: {} resource: {} {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName());
        } catch (RuntimeException e) {
            retryEventQueue.put(resourceUid, event);
            log.warn("Action {} on {} {} failed. Adding to retry queue. Queue length is now {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName(), retryEventQueue.size());
        }
    }

    public void startRetryingQueue() {
        Runnable runnable = () -> {
            retryEventQueue.forEach((resourceUid, event) -> {
                Watcher.Action action = event.getKey();
                CustomResource resource = event.getValue();
                try {
                    eventDispatcher.handleEvent(action, resource);
                    log.info("Retry of action {} on {} {} succeeded, removing from queue.", action, resource.getClass().getSimpleName(),
                            resource.getMetadata().getName());
                    retryEventQueue.remove(resourceUid);
                } catch (RuntimeException e) {
                    log.warn("Retry of action {} on {} {} failed.", action, resource.getClass().getSimpleName(),
                            resource.getMetadata().getName());
                }
            });
        };
        retryExecutor.scheduleAtFixedRate(runnable, 0, retryPeriodSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void onClose(KubernetesClientException e) {
        retryExecutor.shutdown();
    }
}


