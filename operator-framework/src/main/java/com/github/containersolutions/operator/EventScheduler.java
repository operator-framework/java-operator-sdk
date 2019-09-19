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
 * </ul>
 *
 * @param <R>
 */

public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private Map<String, Pair<Action, CustomResource>> customEventQueue = Collections.synchronizedMap(new HashMap<>());

    private EventDispatcher eventDispatcher;

    private Integer BACKOFF = 5;
    private Integer DELAY = 0;

    public <R extends CustomResource> EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }


    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        String resourceUid = resource.getMetadata().getUid();
        Pair<Action, CustomResource> event = new ImmutablePair<>(action, resource);
        try {
            log.debug("Event received for action: {}, {}: {}, on resource: {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName(), resource);
            eventDispatcher.handleEvent(action, resource);
            log.info("Event handling finished for action: {} resource: {}.", action, resource);
        } catch (RuntimeException e) {
            customEventQueue.put(resourceUid, event);
            log.warn("Action {} on {} {} failed. Leaving in queue for retry.", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName());
        }
    }

    public void startRetryingQueue() {
        Runnable runnable = () -> {
            customEventQueue.forEach((resourceUid, event) ->{
                Watcher.Action action = event.getKey();
                CustomResource resource = event.getValue();
                try {
                    eventDispatcher.handleEvent(action, resource);
                    log.info("Retry of action {} on {} {} succeeded, removing from queue.", action, resource.getClass().getSimpleName(),
                            resource.getMetadata().getName());
                    customEventQueue.remove(resourceUid);
                } catch (RuntimeException e) {
                    log.warn("Retry of action {} on {} {} failed.", action, resource.getClass().getSimpleName(),
                            resource.getMetadata().getName());
                }
            });
        };
        ScheduledExecutorService service = Executors
                .newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(runnable, DELAY, BACKOFF, TimeUnit.SECONDS);
    }

    @Override
    public void onClose(KubernetesClientException e) {
    }
}


