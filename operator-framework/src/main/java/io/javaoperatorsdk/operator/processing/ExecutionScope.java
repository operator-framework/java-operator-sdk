package io.javaoperatorsdk.operator.processing;


import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.Event;

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

    @Override
    public String toString() {
        return "ExecutionScope{" +
                "events=" + events +
                ", customResource uid: " + customResource.getMetadata().getUid() +
                ", version: " + customResource.getMetadata().getResourceVersion() +
                '}';
    }
}
