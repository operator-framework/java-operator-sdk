package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.CustomResourceEvent;
import com.github.containersolutions.operator.processing.event.Event;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public class ExecutionUnit {

    private List<Event> list;
    // the latest custom resource
    private CustomResource customResource;
    private String customResourceUid;

    public ExecutionUnit(List<Event> list, CustomResource customResource, String customResourceUid) {
        this.list = list;
        this.customResource = customResource;
        this.customResourceUid = customResourceUid;
    }

    public List<Event> getList() {
        return list;
    }

    public CustomResource getCustomResource() {
        return customResource;
    }

    public String getCustomResourceUid() {
        return customResourceUid;
    }
}
