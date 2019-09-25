package com.github.containersolutions.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class EventConsumer implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(EventConsumer.class);
    private final CustomResourceEvent event;
    private final EventDispatcher eventDispatcher;
    private BackOffExecution backOff;
    private final ScheduledThreadPoolExecutor threadPoolExecutor;
    private final static Integer maxRetries = 10;

    EventConsumer(CustomResourceEvent event, EventDispatcher eventDispatcher, BackOffExecution backOff, ScheduledThreadPoolExecutor threadPoolExecutor) {

        this.event = event;
        this.eventDispatcher = eventDispatcher;
        this.backOff = backOff;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    @Override
    public void run() {
        if (processEvent()) return;

        event.incrementRetryCounter();
        if (event.getRetriesCount() > maxRetries) {
            log.error("Could not process event, retries limit reached.");
            return;
        }

        long backOffTime = backOff.nextBackOff();
        log.warn("Processing event failed, moving back to queue with delay (Retry: {}, Delay: {}ms)", event.getRetriesCount(), backOffTime);
        this.threadPoolExecutor.schedule(this, backOffTime, TimeUnit.MILLISECONDS);

    }

    @SuppressWarnings("unchecked")
    private boolean processEvent() {
        Watcher.Action action = event.getAction();
        CustomResource resource = event.getResource();
        log.info("Processing action {} on {} {}.", action, resource.getClass().getSimpleName(),
                resource.getMetadata().getName());
        try {
            eventDispatcher.handleEvent(action, resource);
        } catch (RuntimeException e) {
            log.warn("Processing action {} on {} {} failed because of '{}'.", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName(), e.getMessage());
            return false;
        }

        return true;
    }
}
