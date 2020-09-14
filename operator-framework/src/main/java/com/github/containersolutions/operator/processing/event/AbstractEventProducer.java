package com.github.containersolutions.operator.processing.event;

public abstract class AbstractEventProducer implements EventProducer {

    protected EventHandler eventHandler;

    @Override
    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public void eventProducerRegistered(String customResourceUid) {}

    @Override
    public void eventProducerDeRegistered(String customResourceUid) {}
}
