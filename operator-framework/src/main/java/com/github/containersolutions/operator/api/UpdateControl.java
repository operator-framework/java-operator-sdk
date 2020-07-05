package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.Optional;

public class UpdateControl<T extends CustomResource> {

    private final T customResource;
    private final boolean updateStatusSubResource;
    private final boolean updateCustomResource;

    private UpdateControl(T customResource, boolean updateStatusSubResource, boolean updateCustomResource) {
        this.customResource = customResource;
        this.updateStatusSubResource = updateStatusSubResource;
        this.updateCustomResource = updateCustomResource;
    }

    public static <T extends CustomResource> UpdateControl updateStatusAndCustomResource(T customResource) {
        return new UpdateControl(customResource, true, true);
    }

    public static <T extends CustomResource> UpdateControl updateCustomResource(T customResource) {
        return new UpdateControl(customResource, false, true);
    }

    public static <T extends CustomResource> UpdateControl updateStatusSubResource(T customResource) {
        return new UpdateControl(customResource, true, false);
    }

    public static UpdateControl noUpdate() {
        return new UpdateControl(null, false, false);
    }

    public Optional<T> getCustomResource() {
        return Optional.ofNullable(customResource);
    }

    public boolean isUpdateStatusSubResource() {
        return updateStatusSubResource;
    }

    public boolean isUpdateCustomResource() {
        return updateCustomResource;
    }
}
