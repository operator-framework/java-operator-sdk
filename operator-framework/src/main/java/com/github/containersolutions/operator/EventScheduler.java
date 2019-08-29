package com.github.containersolutions.operator;


import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    private final static Double INITIAL_SECONDS_BETWEEN_RETRIES = 5d;
    private final static Integer MAX_NUMBER_OF_RETRIES = 3;

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    // ConcurrentHashMap instead for locking at hashmap bucket level?
    private Map<String, Watcher.Action> customResourceMap = Collections.synchronizedMap(new HashMap<>());


    private EventDispatcher eventDispatcher;

    public <R extends CustomResource> EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        try {
            log.debug("Event received for action: {}, {}: {}, on resource: {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName(), resource);

            String resourceUid = resource.getMetadata().getUid();
            if (customResourceMap.containsKey(resourceUid)) {
                customResourceMap.remove(resourceUid);
            } else {
                customResourceMap.put(resourceUid, action);
            }
            eventDispatcher.eventReceived(action, resource);
        } catch (RuntimeException e) {
            rescheduleEvent(action, resource);
        }
    }


    public void rescheduleEvent(Watcher.Action action, CustomResource resource) {

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_NUMBER_OF_RETRIES)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(IntervalFunction.DEFAULT_INITIAL_INTERVAL, INITIAL_SECONDS_BETWEEN_RETRIES))
                .retryOnResult(result -> customResourceMap.containsKey(resource))
                .build();
        Retry retry = Retry.of("eventDispatcher", retryConfig);

        Runnable runnable = () -> {
            eventDispatcher.eventReceived(action, resource);
        };

        Runnable retriableEventHandler = Retry.decorateRunnable(retry, runnable);
        Try<Void> result = Try.run(retriableEventHandler::run);
        log.info("Trying action {} on resource:{} resulted in {}", action.toString(), resource.getMetadata().getName(), result);
    }


    @Override
    public void onClose(KubernetesClientException e) {

    }

}

