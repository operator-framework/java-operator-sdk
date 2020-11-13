package io.javaoperatorsdk.operator.processing.event;

public interface Event<T extends EventSource> {

    String getRelatedCustomResourceUid();

    T getEventSource();
}
