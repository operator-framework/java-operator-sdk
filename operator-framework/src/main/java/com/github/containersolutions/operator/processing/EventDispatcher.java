package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Dispatches events to the Controller and handles Finalizers for a single type of Custom Resource.
 */
public class EventDispatcher<R extends CustomResource> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final ResourceController<R> controller;
    private final CustomResourceOperationsImpl<R, CustomResourceList<R>, CustomResourceDoneable<R>> resourceOperation;
    private final String resourceDefaultFinalizer;
    private final NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>,
            Resource<R, CustomResourceDoneable<R>>> resourceClient;
    private final KubernetesClient k8sClient;

    public EventDispatcher(ResourceController<R> controller,
                           CustomResourceOperationsImpl<R, CustomResourceList<R>, CustomResourceDoneable<R>> resourceOperation,
                           NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>,
                            Resource<R, CustomResourceDoneable<R>>> resourceClient, KubernetesClient k8sClient,
                           String defaultFinalizer

    ) {
        this.controller = controller;
        this.resourceOperation = resourceOperation;
        this.resourceClient = resourceClient;
        this.resourceDefaultFinalizer = defaultFinalizer;
        this.k8sClient = k8sClient;
    }

    public void handleEvent(Watcher.Action action, R resource) {
        log.info("Handling event {} for resource {}", action, resource.getMetadata());
        if (action == Watcher.Action.MODIFIED || action == Watcher.Action.ADDED) {
            // we don't want to call delete resource if it not contains our finalizer,
            // since the resource still can be updates when marked for deletion and contains other finalizers
            if (markedForDeletion(resource) && hasDefaultFinalizer(resource)) {
                boolean removeFinalizer = controller.deleteResource(resource, new Context(k8sClient, resourceClient));
                if (removeFinalizer) {
                    log.debug("Removing finalizer on {}: {}", resource.getMetadata().getName(), resource.getMetadata());
                    removeDefaultFinalizer(resource);
                }
            } else if (!markedForDeletion(resource)){
                Optional<R> updateResult = controller.createOrUpdateResource(resource, new Context<>(k8sClient, resourceClient));
                if (updateResult.isPresent()) {
                    R updatedResource = updateResult.get();
                    log.info("Actual resource in etcd {}", resourceOperation.withName(resource.getMetadata().getName()).get());
                    log.info("Updated resource handled {}", updatedResource.getMetadata());
                    addFinalizerIfNotPresent(updatedResource);
                    replace(updatedResource);
                    // We always add the default finalizer if missing and not marked for deletion.
                } else if (!hasDefaultFinalizer(resource)) {
                    log.info("Actual resource with no finalizer: {}", resourceOperation.withName(resource.getMetadata().getName()).get());
                    addFinalizerIfNotPresent(resource);
                    replace(resource);
                }
            }
        }
        if (Watcher.Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
            return;
        }
        if (Watcher.Action.DELETED == action) {
            log.debug("Resource deleted: {}", resource.getMetadata().getName());
            return;
        }
    }

    private boolean hasDefaultFinalizer(R resource) {
        if (resource.getMetadata().getFinalizers() != null) {
            return resource.getMetadata().getFinalizers().contains(resourceDefaultFinalizer);
        }
        return false;
    }

    private void removeDefaultFinalizer(R resource) {
        resource.getMetadata().getFinalizers().remove(resourceDefaultFinalizer);
        log.debug("Removed finalizer. Trying to replace resource {}, version: {}", resource.getMetadata().getName(), resource.getMetadata().getResourceVersion());
        resourceOperation.lockResourceVersion(resource.getMetadata().getResourceVersion()).replace(resource);
    }

    private void replace(R resource) {
        log.debug("Trying to replace resource {}, version: {}", resource.getMetadata().getName(), resource.getMetadata().getResourceVersion());
        resourceOperation.lockResourceVersion(resource.getMetadata().getResourceVersion()).replace(resource);
    }

    private void addFinalizerIfNotPresent(R resource) {
        if (!hasDefaultFinalizer(resource)) {
            log.info("Adding default finalizer to {}", resource.getMetadata());
            if (resource.getMetadata().getFinalizers() == null) {
                resource.getMetadata().setFinalizers(new ArrayList<>(1));
            }
            resource.getMetadata().getFinalizers().add(resourceDefaultFinalizer);
        }
    }

    private boolean markedForDeletion(R resource) {
        return resource.getMetadata().getDeletionTimestamp() != null && !resource.getMetadata().getDeletionTimestamp().isEmpty();
    }
}
