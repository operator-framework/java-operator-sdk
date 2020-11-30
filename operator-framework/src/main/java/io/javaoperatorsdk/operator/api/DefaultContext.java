package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventList;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

import java.util.List;

public class DefaultContext<T extends CustomResource> implements Context<T> {

    private final EventList events;
    private final EventSourceManager eventSourceManager;

    public DefaultContext(EventSourceManager eventSourceManager, EventList events) {
        this.events = events;
        this.eventSourceManager = eventSourceManager;
    }

    @Override
    public EventSourceManager getEventSourceManager() {
        return eventSourceManager;
    }

    @Override
    public EventList getEvents() {
        return events;
    }

}
