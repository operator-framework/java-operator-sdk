package com.github.containersolutions.operator.processing.event.source;

import com.github.containersolutions.operator.processing.event.EventHandler;
import com.github.containersolutions.operator.processing.event.ExecutionDescriptor;
import io.fabric8.kubernetes.client.CustomResource;

public abstract class AbstractEventSource implements EventSource {

    protected EventHandler eventHandler;
    protected EventSourceManager eventSourceManager;

    @Override
    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public void setEventSourceManager(EventSourceManager eventSourceManager) {
        this.eventSourceManager = eventSourceManager;
    }

    @Override
    public void eventSourceRegisteredForResource(CustomResource customResource) {

    }

    @Override
    public void eventSourceDeRegisteredForResource(String customResourceUid) {

    }

    @Override
    public void controllerExecuted(ExecutionDescriptor executionDescriptor) {

    }
}
