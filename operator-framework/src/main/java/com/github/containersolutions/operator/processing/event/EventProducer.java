package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public abstract class EventProducer {

    protected EventHandler eventHandler;

    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    protected void eventProducerRegistered(CustomResource customResource) {
    }

    protected void eventProducerDeRegistered(CustomResource customResource) {
    }
}
