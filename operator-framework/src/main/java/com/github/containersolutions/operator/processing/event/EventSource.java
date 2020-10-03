package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public interface EventSource {

    void setEventHandler(EventHandler eventHandler);

    void setEventSourceManager(EventSourceManager eventSourceManager);

    void eventProcessingFinished(ExecutionDescriptor executionDescriptor);

    void eventSourceRegisteredForResource(CustomResource customResource);

    void eventSourceDeRegisteredForResource(String customResourceUid);

    void controllerExecuted(ExecutionDescriptor executionDescriptor);
}
