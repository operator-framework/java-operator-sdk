package com.github.containersolutions.operator.processing.event.internal;

import com.github.containersolutions.operator.processing.event.AbstractEventSource;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;

/**
 * Used to trigger buffered events in scheduler
 */
// todo where to register
public class PreviousProcessingCompletedEventSource extends AbstractEventSource {

    @Override
    public void controllerExecuted(ExecutionDescriptor executionDescriptor) {
        eventHandler.handleEvent(new PreviousProcessingCompletedEvent(executionDescriptor.getCustomResourceUid(), this));
    }
}
