package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.Event;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public class ExecutionScope {

    private List<Event> events;
    // the latest custom resource from cache
    private CustomResource customResource;

    public ExecutionScope(List<Event> list, CustomResource customResource) {
        this.events = list;
        this.customResource = customResource;
    }

    public List<Event> getEvents() {
        return events;
    }

    public CustomResource getCustomResource() {
        return customResource;
    }

    public String getCustomResourceUid() {
        return customResource.getMetadata().getUid();
    }
}
