package com.github.containersolutions.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
class ExecutionUnitConsumer implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(ExecutionUnitConsumer.class);

    private final ExecutionScope event;
    private final EventDispatcher eventDispatcher;
    private final EventScheduler eventScheduler;

    ExecutionUnitConsumer(ExecutionScope event, EventDispatcher eventDispatcher, EventScheduler eventScheduler) {
        this.event = event;
        this.eventDispatcher = eventDispatcher;
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void run() {
        PostExecutionControl postExecutionControl = eventDispatcher.handleEvent(event);
        eventScheduler.eventProcessingFinished(event, postExecutionControl);
    }
}
