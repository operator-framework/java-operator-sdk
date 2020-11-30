package io.javaoperatorsdk.operator.processing.event;

public interface Event {

    String getRelatedCustomResourceUid();

    EventSource getEventSource();
}
