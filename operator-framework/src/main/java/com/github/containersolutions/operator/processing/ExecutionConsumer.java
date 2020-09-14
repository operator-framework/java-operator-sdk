package com.github.containersolutions.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionConsumer implements Runnable {
    
    private final static Logger log = LoggerFactory.getLogger(ExecutionConsumer.class);

    private final ExecutionUnit executionUnit;
    private final EventDispatcher eventDispatcher;
    private final EventScheduler eventScheduler;

    public ExecutionConsumer(ExecutionUnit executionUnit, EventDispatcher eventDispatcher, EventScheduler eventScheduler) {
        this.executionUnit = executionUnit;
        this.eventDispatcher = eventDispatcher;
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void run() {
        DispatchControl dispatchControl = eventDispatcher.handleEvent(executionUnit);
        eventScheduler.eventProcessingFinished(executionUnit,dispatchControl);
    }

}
