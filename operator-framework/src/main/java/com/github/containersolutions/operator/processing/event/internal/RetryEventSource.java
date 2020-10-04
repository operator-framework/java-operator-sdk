package com.github.containersolutions.operator.processing.event.internal;

import com.github.containersolutions.operator.processing.event.AbstractEventSource;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import com.github.containersolutions.operator.processing.retry.Retry;

public class RetryEventSource extends AbstractEventSource {

    private Retry retry;

    public RetryEventSource(Retry retry) {
        this.retry = retry;
    }

    @Override
    public void eventProcessingFinished(ExecutionDescriptor executionDescriptor) {


    }
}

