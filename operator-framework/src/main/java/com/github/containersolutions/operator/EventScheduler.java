package com.github.containersolutions.operator;


import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class EventScheduler<R extends CustomResource> implements Watcher<R> {


    private long initialSecondsBetweenRetries;
    private long maxSecondsBetweenRetries;

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    // ConcurrentHashMap instead for locking at hashmap bucket level?
    private Map<String, Boolean> customResourceMap = Collections.synchronizedMap(new HashMap<>());

    private EventDispatcher eventDispatcher;

    public <R extends CustomResource> EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        setInitialSecondsBetweenRetries(1l);
        setMaxSecondsBetweenRetries(30);
    }

    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        try {
            log.debug("Event received for action: {}, {}: {}, on resource: {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName(), resource);

            String resourceUid = resource.getMetadata().getUid();

            if (customResourceMap.containsKey(resourceUid)) {
                // replacing existing resource in map with new resource
                customResourceMap.put(resourceUid, true);
            } else {
                // adding new resource to map
                customResourceMap.put(resourceUid, false);
            }
            eventDispatcher.handleEvent(action, resource);
            log.trace("Event handling finished for action: {} resource: {}", action, resource);
        } catch (RuntimeException e) {
            rescheduleEvent(action, resource);
        }
    }


    private void rescheduleEvent(Watcher.Action action, CustomResource resource) {

        String resourceUid = resource.getMetadata().getUid();
        RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
                .handle(Exception.class)
                .withBackoff(initialSecondsBetweenRetries, maxSecondsBetweenRetries, ChronoUnit.SECONDS)
                .abortWhen(hasNewEventArrived(resourceUid))
                .onFailedAttempt(e -> log.warn("Retry failed", e.getLastFailure()))
                .onRetriesExceeded(e -> log.warn("Max retries exceeded. All failed."))
                .onAbort(e -> log.info("Retries aborted, new event came in."))
                .onSuccess(e -> log.info("Retry successful."));

        CompletableFuture<Void> future = Failsafe.with(retryPolicy).runAsync(() -> eventDispatcher.handleEvent(action, resource));
        log.debug("Future {}",future.toString());
        customResourceMap.remove(resourceUid);
    }

    private Boolean hasNewEventArrived(String resourceUid){
        return customResourceMap.containsKey(resourceUid) && customResourceMap.get(resourceUid);
    }

    @Override
    public void onClose(KubernetesClientException e) {

    }

    public void setInitialSecondsBetweenRetries(long initialSecondsBetweenRetries) {
        this.initialSecondsBetweenRetries = initialSecondsBetweenRetries;
    }
/*    public static long getInitialSecondsBetweenRetries() {
        return initialSecondsBetweenRetries;
    }*/

    public void setMaxSecondsBetweenRetries(long maxSecondsBetweenRetries) {
        this.maxSecondsBetweenRetries = maxSecondsBetweenRetries;
    }
/*
    public static long getMaxSecondsBetweenRetries(){
        return maxSecondsBetweenRetries;
    }*/

}

