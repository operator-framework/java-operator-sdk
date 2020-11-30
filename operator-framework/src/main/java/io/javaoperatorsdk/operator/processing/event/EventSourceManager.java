package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface EventSourceManager {

    <T extends EventSource> void registerEventSource(String name, T eventSource);

    Optional<EventSource> deRegisterCustomResourceFromEventSource(String name, String customResourceUid);

    Map<String, EventSource> getRegisteredEventSources();

}
