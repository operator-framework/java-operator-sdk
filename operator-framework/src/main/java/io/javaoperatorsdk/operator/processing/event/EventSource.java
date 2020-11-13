package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public interface EventSource {

    void setEventHandler(EventHandler eventHandler);

    void setEventSourceManager(EventSourceManager eventSourceManager);

    void eventSourceRegisteredForResource(CustomResource customResource);

    void eventSourceDeRegisteredForResource(String customResourceUid);

    void controllerExecuted(ExecutionDescriptor executionDescriptor);
}
