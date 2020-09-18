package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import io.fabric8.kubernetes.client.CustomResource;

public interface EventSource {

    void setEventHandler(EventHandler eventHandler);

    void eventSourceRegistered(String customResourceUid);

    void eventSourceDeRegistered(String customResourceUid);

    void eventProcessingFinished(ExecutionDescriptor executionDescriptor);
}
