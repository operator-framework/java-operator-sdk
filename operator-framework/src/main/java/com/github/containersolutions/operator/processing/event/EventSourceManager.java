package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EventSourceManager {

    void registerEventSource(CustomResource customResource,String name, EventSource eventSource);

    Optional<EventSource> deRegisterEventSource(String customResourceUid, String name);

    Map<String,EventSource> getRegisteredEventSources(String customResource);


}
