package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.Event;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public class ExecutionUnit {

    private List<Event> list;
    // the latest custom resource
    private CustomResource customResource;

    public ExecutionUnit(List<Event> list, CustomResource customResource) {
        this.list = list;
        this.customResource = customResource;
    }

    public List<Event> getList() {
        return list;
    }

    public CustomResource getCustomResource() {
        return customResource;
    }

    public String getCustomResourceUid() {
        return customResource.getMetadata().getUid();
    }
}
