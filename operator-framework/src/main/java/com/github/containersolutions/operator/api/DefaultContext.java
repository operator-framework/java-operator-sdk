package com.github.containersolutions.operator.api;

import com.github.containersolutions.operator.processing.event.Event;
import com.github.containersolutions.operator.processing.event.EventSourceManager;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public class DefaultContext<T extends CustomResource> implements Context<T> {

    private final RetryInfo retryInfo;
    private final List<Event> events;

    public DefaultContext(RetryInfo retryInfo) {
        this.retryInfo = retryInfo;
    }

    @Override
    public RetryInfo retryInfo() {
        return retryInfo;
    }

    @Override
    public EventSourceManager getEventManager() {
        return null;
    }

    @Override
    public List<Event> getEvents() {
        return null;
    }
}
