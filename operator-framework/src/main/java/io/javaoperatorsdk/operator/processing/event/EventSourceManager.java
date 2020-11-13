package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EventSourceManager {

    <T extends EventSource> void registerEventSource(CustomResource customResource, String name, T eventSource);

    <T extends EventSource> T registerEventSourceIfNotRegistered(CustomResource customResource, String name, T eventSource);

    Optional<EventSource> deRegisterEventSource(String customResourceUid, String name);

    Map<String, EventSource> getRegisteredEventSources(String customResource);


}
