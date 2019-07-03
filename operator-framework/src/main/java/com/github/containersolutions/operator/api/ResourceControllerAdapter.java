package com.github.containersolutions.operator.api;

import com.github.containersolutions.operator.Context;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.Optional;

/**
 * Provides a more clear interface for the most common use case.
 *
 * @param <R>
 */
public abstract class ResourceControllerAdapter<R extends CustomResource> implements ResourceController<R> {


    @Override
    public boolean deleteResource(R resource, Context<R> context) {
        delete(resource, context);
        return true;
    }

    @Override
    public Optional<R> createOrUpdateResource(R resource, Context<R> context) {
        createOrUpdate(resource, context);
        return Optional.of(resource);
    }

    public abstract void delete(R resource, Context<R> context);

    public abstract void createOrUpdate(R resource, Context<R> context);
}
