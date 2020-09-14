package com.github.containersolutions.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
class ExecutionUnitConsumer implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(ExecutionUnitConsumer.class);

    private final ExecutionUnit event;
    private final EventDispatcher eventDispatcher;
    private final EventScheduler eventScheduler;

    ExecutionUnitConsumer(ExecutionUnit event, EventDispatcher eventDispatcher, EventScheduler eventScheduler) {
        this.event = event;
        this.eventDispatcher = eventDispatcher;
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void run() {
        DispatchControl dispatchControl = eventDispatcher.handleEvent(event);
        eventScheduler.eventProcessingFinished(event, dispatchControl);
    }
}
