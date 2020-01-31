package com.github.containersolutions.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EventConsumer implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(EventConsumer.class);
    private final CustomResourceEvent event;
    private final EventDispatcher eventDispatcher;
    private final EventScheduler eventScheduler;

    EventConsumer(CustomResourceEvent event, EventDispatcher eventDispatcher, EventScheduler eventScheduler) {
        this.event = event;
        this.eventDispatcher = eventDispatcher;
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void run() {
        boolean stillScheduledForProcessing = eventScheduler.eventProcessingStarted(event);
        if (!stillScheduledForProcessing) {
            return;
        }
        if (processEvent()) {
            eventScheduler.eventProcessingFinishedSuccessfully(event);
        } else {
            this.eventScheduler.eventProcessingFailed(event);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean processEvent() {

        Watcher.Action action = event.getAction();
        CustomResource resource = event.getResource();
        log.info("Processing event {}", event);
        try {
            eventDispatcher.handleEvent(action, resource);
        } catch (RuntimeException e) {
            log.error("Processing event {} failed.", event, e);
            log.debug("Failed object: {}", resource);
            return false;
        }

        return true;
    }
}
