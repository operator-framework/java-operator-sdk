package com.github.containersolutions.operator;


import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventScheduler {

    private final static Double INITIAL_SECONDS_BETWEEN_RETRIES = 5d;
    private final static Integer MAX_NUMBER_OF_RETRIES = 3;

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private EventDispatcher eventDispatcher;
    // ConcurrentHashMap instead for locking at hashmap bucket level?
    private Map<CustomResource, Watcher.Action> customResourceMap = Collections.synchronizedMap(new HashMap<>());
    private RetryConfig retryConfig;

    public EventScheduler(EventDispatcher eventDispatcher) {
        this.retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_NUMBER_OF_RETRIES)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(IntervalFunction.DEFAULT_INITIAL_INTERVAL, INITIAL_SECONDS_BETWEEN_RETRIES))
                //.retryOnExceptionPredicate(!customResourceMap.containsKey(resource))
                .build();
        this.eventDispatcher = eventDispatcher;
    }


    public <R extends CustomResource> void rescheduleEvent(Watcher.Action action, CustomResource resource) {
        customResourceMap.put(resource, action);

        Retry retry = Retry.of("eventDispatcher", retryConfig);

        Runnable runnable = () -> {
            eventDispatcher.handleEvent(action, resource);
        };
        Runnable retriableEventHandler = Retry.decorateRunnable(retry,runnable);
        Try<Void> result = Try.run(retriableEventHandler::run);
        log.info("Result {} when trying action {} on resource:{}", result, action.toString(), resource.getMetadata().getName());

 /* TODO Is a circuitbreaker necessary to end retries early?
            if (!customResourceMap.containsKey(resource)){
            log.info("New event for resource {} while retrying action {}, interrupting retry process.", resource.getMetadata().getName(), action);
            log.info("Successful retry of action {} on resource:{}", action.toString(), resource.getMetadata().getName());
            log.error("Error on final retry, action {} on resource: {}", action.toString(), resource.getMetadata().getName(), ree);
            log.error("Error while retrying action {} on resource: {}", action.toString(), resource.getMetadata().getName(), e);
        }*/
    }

    public void eventArrived(Watcher.Action action, CustomResource resource) {
        if (customResourceMap.containsKey(resource)) {
            customResourceMap.remove(resource);
        }
    }


}
