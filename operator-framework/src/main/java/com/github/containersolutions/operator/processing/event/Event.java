package com.github.containersolutions.operator.processing.event;

import io.fabric8.kubernetes.client.CustomResource;

public class Event {

    private CustomResource customResource;

    public Event(CustomResource customResource) {
        this.customResource = customResource;
    }

    public CustomResource getCustomResource() {
        return customResource;
    }
}
