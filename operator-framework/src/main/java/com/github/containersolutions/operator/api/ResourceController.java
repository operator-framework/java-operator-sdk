package com.github.containersolutions.operator.api;

import com.github.containersolutions.operator.Context;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.Optional;

public interface ResourceController<R extends CustomResource> {

    void deleteResource(R resource, Context<R> context);

    /**
     * The implementation of this operation is required to be idempotent.
     *
     * @return The resource is updated in api server if the return value is present
     *  within Optional. This the common use cases. However in cases, for example the operator is restarted,
     *  and we don't want to have an update call to k8s api to be made unnecessarily, by returning an empty Optional
     *  this update can be skipped.
     *  <b>However we will always call an update if there is no finalizer on object and its not marked for deletion.</b>
     */
    Optional<R> createOrUpdateResource(R resource, Context<R> context);

}
