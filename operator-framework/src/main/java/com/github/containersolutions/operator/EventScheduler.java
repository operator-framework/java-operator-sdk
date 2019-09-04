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

public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    // ConcurrentHashMap instead for locking at hashmap bucket level?
    private Map<String, Pair<Action, CustomResource>> customEventQueue = Collections.synchronizedMap(new HashMap<>());

    private EventDispatcher eventDispatcher;

    private Integer BACKOFF = 5;
    private Integer DELAY = 0;

    public <R extends CustomResource> EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        startRetryingQueue();
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

    private void startRetryingQueue() {
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


