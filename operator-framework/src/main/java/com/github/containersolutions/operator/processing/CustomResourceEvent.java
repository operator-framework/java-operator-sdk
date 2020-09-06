package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.Event;
import com.github.containersolutions.operator.processing.retry.Retry;
import com.github.containersolutions.operator.processing.retry.RetryExecution;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;

import java.util.Optional;

public class CustomResourceEvent extends Event {

    private final RetryExecution retryExecution;
    private final Watcher.Action action;

    private int retryCount = -1;
    private boolean processRegardlessOfGeneration = false;

    public CustomResourceEvent(Watcher.Action action, CustomResource resource, Retry retry) {
        super(resource);
        this.action = action;

        this.retryExecution = retry.initExecution();
    }

    public boolean isProcessRegardlessOfGeneration() {
        return processRegardlessOfGeneration;
    }

    public void setProcessRegardlessOfGeneration(boolean processRegardlessOfGeneration) {
        this.processRegardlessOfGeneration = processRegardlessOfGeneration;
    }

    public Watcher.Action getAction() {
        return action;
    }

    public String resourceUid() {
        return getCustomResource().getMetadata().getUid();
    }

    public Optional<Long> nextBackOff() {
        retryCount++;
        return retryExecution.nextDelay();
    }

    @Override
    public String toString() {
        return "CustomResourceEvent{" +
                "action=" + action +
                ", resource=[ name=" + getCustomResource().getMetadata().getName() + ", kind=" + getCustomResource().getKind() +
                ", apiVersion=" + getCustomResource().getApiVersion() + " ,resourceVersion=" + getCustomResource().getMetadata().getResourceVersion() +
                ", markedForDeletion: " + (getCustomResource().getMetadata().getDeletionTimestamp() != null
                && !getCustomResource().getMetadata().getDeletionTimestamp().isEmpty()) +
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
