package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface EventSourceManager {

    <T extends EventSource> void registerEventSource(CustomResource customResource, String name, T eventSource);

    <T extends EventSource> T registerEventSourceIfNotRegistered(CustomResource customResource, String name, Supplier<T> eventSource);

    Optional<EventSource> deRegisterEventSource(String customResourceUid, String name);

    Map<String, EventSource> getRegisteredEventSources(String customResourceUid);


}
