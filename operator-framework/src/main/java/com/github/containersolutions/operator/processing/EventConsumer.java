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
        DispatchControl dispatchControl = eventDispatcher.handleEvent(event);
        eventScheduler.eventProcessingFinished(event, dispatchControl);
//        log.debug("Processing event started: {}", event);
//        try {
//            DispatchControl dispatchControl = eventDispatcher.handleEvent(event);
//            eventScheduler.eventProcessingFinishedSuccessfully(event, dispatchControl);
//            log.debug("Event processed successfully: {}", event);
//        } catch (RuntimeException e) {
//            log.error("Processing event {} failed.", event, e);
//            this.eventScheduler.eventProcessingFailed(event);
//        }
    }
}
