package com.github.containersolutions.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

class CustomResourceEvent {
    private final static Logger log = LoggerFactory.getLogger(CustomResourceEvent.class);

    private final Watcher.Action action;
    private final CustomResource resource;
    private Integer retriesCount = 0;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomResourceEvent that = (CustomResourceEvent) o;
        // note that action is not interesting for us since, we schedule only modified and added events, and we decide
        // between those inside dispatcher not based on fabric8 client
        return sameResourceAs(that);
    }

    @Override
    public int hashCode() {
        // todo this probably needs to be improved use the api version + kind + name
        return Objects.hash(resource);
    }

    public Boolean sameResourceAs(CustomResourceEvent otherEvent) {
        return getResource().getKind().equals(otherEvent.getResource().getKind()) &&
                getResource().getApiVersion().equals(otherEvent.getResource().getApiVersion()) &&
                getResource().getMetadata().getName().equals(otherEvent.getResource().getMetadata().getName());
    }

    public Boolean isSameResourceAndNewerGeneration(CustomResourceEvent otherEvent) {
        return sameResourceAs(otherEvent) &&
                getResource().getMetadata().getGeneration() > otherEvent.getResource().getMetadata().getGeneration();

    }


}
