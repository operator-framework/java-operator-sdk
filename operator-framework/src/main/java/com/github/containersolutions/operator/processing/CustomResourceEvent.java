package com.github.containersolutions.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Optional;

public class CustomResourceEvent {

    public static final long INITIAL_BACK_OFF_INTERVAL = 2000L;

    public static final int MAX_RETRY_COUNT = 5;
    public static final double BACK_OFF_MULTIPLIER = 1.5;
    private final static Logger log = LoggerFactory.getLogger(CustomResourceEvent.class);
    private final static BackOffExecution backOff = new ExponentialBackOff(INITIAL_BACK_OFF_INTERVAL, BACK_OFF_MULTIPLIER).start();

    private final Watcher.Action action;
    private final CustomResource resource;
    private Integer retryIndex = -1;

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

    public String resourceUid() {
        return resource.getMetadata().getUid();
    }

    public Boolean sameResourceAs(CustomResourceEvent otherEvent) {
        return getResource().getMetadata().getUid().equals(otherEvent.getResource().getMetadata().getUid());
    }

    public Boolean isSameResourceAndNewerVersion(CustomResourceEvent otherEvent) {
        return sameResourceAs(otherEvent) &&
                Long.parseLong(getResource().getMetadata().getResourceVersion()) >
                        Long.parseLong(otherEvent.getResource().getMetadata().getResourceVersion());

    }

    public Optional<Long> nextBackOff() {
        if (retryIndex == -1) {
            retryIndex = 0;
            return Optional.of(0l);
        } else {
            if (retryIndex >= MAX_RETRY_COUNT - 1) {
                return Optional.empty();
            } else {
                retryIndex++;
                return Optional.of(backOff.nextBackOff());
            }
        }
    }

    @Override
    public String toString() {
        return "CustomResourceEvent{" +
                "action=" + action +
                ", resource=[ name=" + resource.getMetadata().getName() + ", kind=" + resource.getKind() +
                ", apiVersion=" + resource.getApiVersion() + " ,resourceVersion=" + resource.getMetadata().getResourceVersion() +
                ", markerForDeletion: " + (resource.getMetadata().getDeletionTimestamp() != null
                && !resource.getMetadata().getDeletionTimestamp().isEmpty()) +
                " ], retriesIndex=" + retryIndex +
                '}';
    }
}
