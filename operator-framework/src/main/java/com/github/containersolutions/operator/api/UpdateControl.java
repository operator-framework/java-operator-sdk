package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.client.CustomResource;

public class UpdateControl<T extends CustomResource> {

    private T customResource;
    private UpdateMode updateMode;

    private UpdateControl(T customResource, UpdateMode updateMode) {
        this.customResource = customResource;
        this.updateMode = updateMode;
    }

    public static <T extends CustomResource> UpdateControl updateStatusAndeCustomResource(T customResource) {
        return new UpdateControl(customResource, UpdateMode.STATUS_AND_CUSTOM_RESOURCE);
    }

    public static <T extends CustomResource> UpdateControl updateCustomResource(T customResource) {
        return new UpdateControl(customResource, UpdateMode.CUSTOM_RESOURCE);
    }

    public static <T extends CustomResource> UpdateControl updateStatusSubResource(T customResource) {
        return new UpdateControl(customResource, UpdateMode.STATUS);
    }

    public static UpdateControl noUpdate() {
        return new UpdateControl(null, UpdateMode.NO_UPDATE);
    }

    public T getCustomResource() {
        return customResource;
    }

    public UpdateMode getUpdateMode() {
        return updateMode;
    }

    public enum UpdateMode {
        STATUS_AND_CUSTOM_RESOURCE,
        STATUS,
        CUSTOM_RESOURCE,
        NO_UPDATE
    }
}
