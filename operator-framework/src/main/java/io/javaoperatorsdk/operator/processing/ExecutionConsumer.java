package io.javaoperatorsdk.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionConsumer implements Runnable {
    
    private final static Logger log = LoggerFactory.getLogger(ExecutionConsumer.class);

    private final ExecutionScope executionScope;
    private final EventDispatcher eventDispatcher;
    private final EventScheduler eventScheduler;

    public ExecutionConsumer(ExecutionScope executionScope, EventDispatcher eventDispatcher, EventScheduler eventScheduler) {
        this.executionScope = executionScope;
        this.eventDispatcher = eventDispatcher;
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void run() {
        PostExecutionControl postExecutionControl = eventDispatcher.handleEvent(executionScope);
        eventScheduler.eventProcessingFinished(executionScope, postExecutionControl);
    }

}
