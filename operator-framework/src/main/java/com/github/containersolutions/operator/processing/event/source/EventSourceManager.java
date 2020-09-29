package com.github.containersolutions.operator.processing.event.source;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public interface EventSourceManager {

    void registerEventSource(CustomResource customResource, EventSource eventSource);

    void deRegisterEventProducer(String customResourceUid, EventSource eventSource);

    List<EventSource> getRegisteredEventSources(String customResource);


}
