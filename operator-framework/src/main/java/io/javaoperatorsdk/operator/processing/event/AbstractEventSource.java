package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public abstract class AbstractEventSource implements EventSource {

    protected EventHandler eventHandler;

    @Override
    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public void eventSourceDeRegisteredForResource(String customResourceUid) {
    }

}
