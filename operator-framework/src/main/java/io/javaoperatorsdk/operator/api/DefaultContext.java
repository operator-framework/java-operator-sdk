package io.javaoperatorsdk.operator.api;

import com.github.containersolutions.operator.processing.event.Event;
import com.github.containersolutions.operator.processing.event.EventSourceManager;
import io.fabric8.kubernetes.client.CustomResource;

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
