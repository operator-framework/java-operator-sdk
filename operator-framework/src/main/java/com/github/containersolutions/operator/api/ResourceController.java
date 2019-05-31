package com.github.containersolutions.operator.api;

import com.github.containersolutions.operator.Context;
import io.fabric8.kubernetes.client.CustomResource;

public interface ResourceController<R extends CustomResource> {

    void deleteResource(R resource, Context<R> context);

    /*
     * This operation is required to be idempotent.
     */
    R createOrUpdateResource(R resource, Context<R> context);

}
