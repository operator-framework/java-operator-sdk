package com.github.containersolutions.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

class CustomResourceEvent {
    private final static Logger log = LoggerFactory.getLogger(CustomResourceEvent.class);

    private final Watcher.Action action;
    private final CustomResource resource;
    private Integer retriesIndex = 0;

    CustomResourceEvent(Watcher.Action action, CustomResource resource) {
        this.action = action;
        this.resource = resource;
    }

    Watcher.Action getAction() {
        return action;
    }

    public CustomResource getResource() {
        return resource;
    }

    public String getEventInfo() {
        CustomResource resource = this.getResource();
        return new StringBuilder().
                append("Resource ").append(getAction().toString().toLowerCase()).append(" -> ").
                append(Optional.ofNullable(resource.getMetadata().getNamespace()).orElse("cluster")).append("/").
                append(resource.getKind()).append(":").
                append(resource.getMetadata().getName()).toString();

    }

    public String resourceKey() {
        return resource.getKind() + "_" + resource.getMetadata().getName();
    }

    public Boolean sameResourceAs(CustomResourceEvent otherEvent) {
        return getResource().getKind().equals(otherEvent.getResource().getKind()) &&
                getResource().getApiVersion().equals(otherEvent.getResource().getApiVersion()) &&
                getResource().getMetadata().getName().equals(otherEvent.getResource().getMetadata().getName());
    }

    public Boolean isSameResourceAndNewerVersion(CustomResourceEvent otherEvent) {
        return sameResourceAs(otherEvent) &&
                Long.parseLong(getResource().getMetadata().getResourceVersion()) >
                        Long.parseLong(otherEvent.getResource().getMetadata().getResourceVersion());

    }

    @Override
    public String toString() {
        return "CustomResourceEvent{" +
                "action=" + action +
                ", resource=[ name=" + resource.getMetadata().getName() + ", kind=" + resource.getKind() +
                ", apiVersion=" + resource.getApiVersion() + "]" +
                ", retriesIndex=" + retriesIndex +
                '}';
    }
}
