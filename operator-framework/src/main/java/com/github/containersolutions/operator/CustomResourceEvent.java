package com.github.containersolutions.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.springframework.util.backoff.BackOffExecution;

class CustomResourceEvent {
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

    void incrementRetryCounter() {
        this.retriesCount++;
    }

    Integer getRetriesCount() {
        return retriesCount;
    }
}
