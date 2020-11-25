package io.javaoperatorsdk.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ExecutionConsumer implements Runnable {
    
    private final static Logger log = LoggerFactory.getLogger(ExecutionConsumer.class);

    private final ExecutionScope executionScope;
    private final EventDispatcher eventDispatcher;
    private final DefaultEventHandler defaultEventHandler;

    public ExecutionConsumer(ExecutionScope executionScope, EventDispatcher eventDispatcher, DefaultEventHandler defaultEventHandler) {
        this.executionScope = executionScope;
        this.eventDispatcher = eventDispatcher;
        this.defaultEventHandler = defaultEventHandler;
    }

    @Override
    public void run() {
        PostExecutionControl postExecutionControl = eventDispatcher.handleEvent(executionScope);
        defaultEventHandler.eventProcessingFinished(executionScope, postExecutionControl);
    }

}
