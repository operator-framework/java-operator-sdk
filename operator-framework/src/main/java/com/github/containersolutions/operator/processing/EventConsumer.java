package com.github.containersolutions.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
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
        log.debug("Processing event started: {}", event);
        if (processEvent()) {
            eventScheduler.eventProcessingFinishedSuccessfully(event);
            log.debug("Event processed successfully: {}", event);
        } else {
            this.eventScheduler.eventProcessingFailed(event);
            log.debug("Event processed failed: {}", event);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean processEvent() {
        try {
            eventDispatcher.handleEvent(event);
        } catch (RuntimeException e) {
            log.error("Processing event {} failed.", event, e);
            return false;
        }
        return true;
    }
}
