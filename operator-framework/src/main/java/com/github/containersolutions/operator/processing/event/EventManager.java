package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public interface EventManager {

    void registerEventProducer(String customResource, EventProducer eventSource);

    void deRegisterEventProducer(String customResource, EventProducer eventSource);

    List<EventProducer> getRegisteredEventSources(String customResource);
}
