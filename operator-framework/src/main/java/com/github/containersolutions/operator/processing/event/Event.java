package com.github.containersolutions.operator.processing.event;

public interface Event {

    String getRelatedCustomResourceUid();

    EventSource getEventSource();
}
