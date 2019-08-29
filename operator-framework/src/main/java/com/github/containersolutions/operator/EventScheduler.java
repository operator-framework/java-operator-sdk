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

public class EventScheduler<T extends CustomResource> implements Watcher<T> {

    private final static Double INITIAL_SECONDS_BETWEEN_RETRIES = 5d;
    private final static Integer MAX_NUMBER_OF_RETRIES = 3;

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private Map<CustomResource, EventDispatcher> eventDispatchers = Collections.synchronizedMap(new HashMap<>());
    // ConcurrentHashMap instead for locking at hashmap bucket level?
    private Map<CustomResource, Watcher.Action> customResourceMap = Collections.synchronizedMap(new HashMap<>());

    public static EventScheduler eventScheduler = new EventScheduler();

    private Map<String, EventDispatcher> eventDispatcher;

    @Override
    public void eventReceived(Action action, T t) {
        try {
//            eventDispatcher.eventReceived(action, t);
        } catch (RuntimeException e) {
//          ....
        }
    }

    public void rescheduleEvent(Watcher.Action action, CustomResource resource) {
        customResourceMap.put(resource, action);
        EventDispatcher eventDispatcher = eventDispatchers.get(resource);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_NUMBER_OF_RETRIES)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(IntervalFunction.DEFAULT_INITIAL_INTERVAL, INITIAL_SECONDS_BETWEEN_RETRIES))
                .retryOnResult(result -> customResourceMap.containsKey(resource))
                .build();
        Retry retry = Retry.of("eventDispatcher", retryConfig);

        Runnable runnable = () -> {
            eventDispatcher.handleEvent(action, resource);
        };

        Runnable retriableEventHandler = Retry.decorateRunnable(retry, runnable);
        Try<Void> result = Try.run(retriableEventHandler::run);
        log.info("Trying action {} on resource:{} resulted in {}", action.toString(), resource.getMetadata().getName(), result);
        eventDispatchers.remove(resource);
    }

    public void eventArrived(Watcher.Action action, CustomResource resource) {
        if (customResourceMap.containsKey(resource)) {
            customResourceMap.remove(resource);
        }
    }

    public void registerDispatcher(EventDispatcher eventDispatcher, CustomResource resource){
        eventDispatchers.put(resource, eventDispatcher);
    }


    @Override
    public void onClose(KubernetesClientException e) {

    }
}
