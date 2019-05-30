package jkube.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import jkube.operator.Context;

public interface ResourceController<R extends CustomResource> {

    void deleteResource(R resource, Context<R> context);

    R createOrUpdateResource(R resource, Context<R> context);

}
