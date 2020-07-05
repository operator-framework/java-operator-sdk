package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.retry.Retry;
import com.github.containersolutions.operator.processing.retry.RetryExecution;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;

import java.util.Optional;

public class CustomResourceEvent {

    private final RetryExecution retryExecution;
    private final Watcher.Action action;
    private final CustomResource resource;
    private int retryCount = -1;

    public CustomResourceEvent(Watcher.Action action, CustomResource resource, Retry retry) {
        this.action = action;
        this.resource = resource;
        this.retryExecution = retry.initExecution();
    }

    public Watcher.Action getAction() {
        return action;
    }

    public CustomResource getResource() {
        return resource;
    }

    public String resourceUid() {
        return resource.getMetadata().getUid();
    }

    public Optional<Long> nextBackOff() {
        retryCount++;
        return retryExecution.nextDelay();
    }

    @Override
    public String toString() {
        return "CustomResourceEvent{" +
                "action=" + action +
                ", resource=[ name=" + resource.getMetadata().getName() + ", kind=" + resource.getKind() +
                ", apiVersion=" + resource.getApiVersion() + " ,resourceVersion=" + resource.getMetadata().getResourceVersion() +
                ", markedForDeletion: " + (resource.getMetadata().getDeletionTimestamp() != null
                && !resource.getMetadata().getDeletionTimestamp().isEmpty()) +
                " ], retriesIndex=" + retryCount +
                '}';
    }

    public RetryExecution getRetryExecution() {
        return retryExecution;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
