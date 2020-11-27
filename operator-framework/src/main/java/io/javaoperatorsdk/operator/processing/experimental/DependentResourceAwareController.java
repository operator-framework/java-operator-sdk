package io.javaoperatorsdk.operator.processing.experimental;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

public class DependentResourceAwareController<T extends CustomResource> implements ResourceController<T> {

    @Override
    final public DeleteControl deleteResource(T resource, Context<T> context) {
        return null;
    }

    @Override
    final public UpdateControl<T> createOrUpdateResource(T resource, Context<T> context) {
        return null;
    }

    public DeleteControl deleteResource(T resource, DependentResourceContext<T> context) {
        return null;
    }

    public UpdateControl<T> createOrUpdateResource(T resource, DependentResourceContext<T> context) {
        return null;
    }

}
