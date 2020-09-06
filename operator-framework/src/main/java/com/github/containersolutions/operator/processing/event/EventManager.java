package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public interface EventManager {

    void registerEventProducer(CustomResource customResource, EventProducer eventSource);

    void deRegisterEventProducer(CustomResource customResource, EventProducer eventSource);

    List<EventProducer> getRegisteredEventSources(CustomResource customResource);
}
