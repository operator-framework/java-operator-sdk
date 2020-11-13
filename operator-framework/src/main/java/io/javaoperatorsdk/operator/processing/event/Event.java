package com.github.containersolutions.operator.processing.event;

public interface Event<T extends EventSource> {

    String getRelatedCustomResourceUid();

    T getEventSource();
}
