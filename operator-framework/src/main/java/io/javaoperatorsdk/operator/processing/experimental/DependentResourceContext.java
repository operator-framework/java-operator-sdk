package io.javaoperatorsdk.operator.processing.experimental;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

import java.util.List;

public class DependentResourceContext<T extends CustomResource> implements Context<T> {

    @Override
    public EventSourceManager getEventSourceManager() {
        return null;
    }

    @Override
    public List<Event> getEvents() {
        return null;
    }


}
