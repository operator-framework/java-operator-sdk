package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public interface EventProducerManager {

    void registerEventProducer(String customResourceUid, EventProducer eventSource);

    void deRegisterEventProducer(String customResourceUid, EventProducer eventSource);

    List<EventProducer> getRegisteredEventProducers(String customResource);
}
