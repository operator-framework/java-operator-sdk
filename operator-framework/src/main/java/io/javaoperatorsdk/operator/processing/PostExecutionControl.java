package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.UpdateControl;

import java.util.Optional;

public final class PostExecutionControl {

    private final boolean onlyFinalizerHandled;

    private final CustomResource updatedCustomResource;

    private PostExecutionControl(boolean onlyFinalizerHandled, CustomResource updatedCustomResource) {
        this.onlyFinalizerHandled = onlyFinalizerHandled;
        this.updatedCustomResource = updatedCustomResource;
    }

    public static PostExecutionControl onlyFinalizerAdded() {
        return new PostExecutionControl(true, null);
    }

    public static PostExecutionControl defaultDispatch() {
        return new PostExecutionControl(false, null);
    }

    public static PostExecutionControl customResourceUpdated(CustomResource updatedCustomResource) {
        return new PostExecutionControl(false, updatedCustomResource);
    }

    public boolean isOnlyFinalizerHandled() {
        return onlyFinalizerHandled;
    }

    public Optional<CustomResource> getUpdatedCustomResource() {
        return Optional.ofNullable(updatedCustomResource);
    }

    public boolean customResourceUpdatedDuringExecution() {
        return updatedCustomResource != null;
    }
}
