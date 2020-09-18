package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.source.EventSource;

public abstract class AbstractEvent implements Event {

    private final String relatedCustomResourceUid;

    private final EventSource eventSource;

    public AbstractEvent(String relatedCustomResourceUid, EventSource eventSource) {
        this.relatedCustomResourceUid = relatedCustomResourceUid;
        this.eventSource = eventSource;
    }

    @Override
    public String getRelatedCustomResourceUid() {
        return relatedCustomResourceUid;
    }

    @Override
    public EventSource getEventSource() {
        return eventSource;
    }
}
