package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

import java.util.List;

public class DefaultContext<T extends CustomResource> implements Context<T> {

    private final CustomResource customResource;
    private final RetryInfo retryInfo;
    private final List<Event> events;

    public DefaultContext(CustomResource customResource, RetryInfo retryInfo, List<Event> events) {
        this.customResource = customResource;
        this.retryInfo = retryInfo;
        this.events = events;
    }

    public RetryInfo retryInfo() {
        return retryInfo;
    }

    @Override
    public EventSourceManager getEventSourceManager() {
        return null;
    }

    @Override
    public List<Event> getEvents() {
        return null;
    }
}
