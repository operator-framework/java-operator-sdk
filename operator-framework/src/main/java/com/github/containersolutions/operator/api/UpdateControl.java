package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.client.CustomResource;

public class UpdateControl<T extends CustomResource> {

    private final T customResource;
    private final boolean updateStatusSubResource;
    private final boolean updateCustomResource;

    private UpdateControl(T customResource, boolean updateStatusSubResource, boolean updateCustomResource) {
        if ((updateCustomResource || updateStatusSubResource) && customResource == null) {
            throw new IllegalArgumentException("CustomResource cannot be null in case of update");
        }
        this.customResource = customResource;
        this.updateStatusSubResource = updateStatusSubResource;
        this.updateCustomResource = updateCustomResource;
    }

    public static <T extends CustomResource> UpdateControl<T> updateCustomResource(T customResource) {
        return new UpdateControl<>(customResource, false, true);
    }

    public static <T extends CustomResource> UpdateControl<T> updateStatusSubResource(T customResource) {
        return new UpdateControl<>(customResource, true, false);
    }

    public static <T extends CustomResource> UpdateControl<T> noUpdate() {
        return new UpdateControl<>(null, false, false);
    }

    public T getCustomResource() {
        return customResource;
    }

    public boolean isUpdateStatusSubResource() {
        return updateStatusSubResource;
    }

    public boolean isUpdateCustomResource() {
        return updateCustomResource;
    }

}
