package com.github.containersolutions.operator.processing.event.source;

import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;

public interface GenericEventSource {

    void setEventHandler(EventHandler eventHandler);

    void eventProcessingFinished(ExecutionDescriptor executionDescriptor);
}
