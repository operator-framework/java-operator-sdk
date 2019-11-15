package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

// instance per resource type
class EventDispatcher<R extends CustomResource> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final ResourceController<R> controller;
    private final CustomResourceOperationsImpl<R, CustomResourceList<R>, CustomResourceDoneable<R>> resourceOperation;
    private final String resourceDefaultFinalizer;
    private final NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>,
            Resource<R, CustomResourceDoneable<R>>> resourceClient;
    private final KubernetesClient k8sClient;

    EventDispatcher(ResourceController<R> controller,
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


    void handleEvent(Watcher.Action action, R resource) {


        CustomResourceDefinitionContext context = new CustomResourceDefinitionContext.Builder()
                .withGroup(resourceOperation.getAPIGroup())
                .withName(resourceOperation.getName())
                .withVersion(resourceOperation.getAPIVersion())
                .withScope(resourceOperation.isResourceNamespaced() ? "Namespaced" : "Cluster")
                .withPlural(resourceOperation.getResourceT()).build();

        if (Watcher.Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
            return;
        }
        if (Watcher.Action.DELETED == action) {
            log.debug("Resource deleted: {}", resource.getMetadata().getName());
            return;
        }

        Long thisVersion = resource.getMetadata().getGeneration();
        Map<String, Object> object = k8sClient.customResource(context).get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
        Map<String, Object> metadata = (Map<String, Object>) object.get("metadata");
        Long generation = Long.valueOf(metadata.getOrDefault("generation", 0).toString());

        log.debug("This generation is {} and latest is {} for resource {}", thisVersion, generation, resource.getMetadata().getName());
        if (generation > thisVersion) {
            log.warn("Skipping event because it's not latest modification. This generation is {} and latest is {} for resource {}", thisVersion, generation, resource.getMetadata().getName());
            return;
        }

        if (action == Watcher.Action.MODIFIED || action == Watcher.Action.ADDED) {
            // we don't want to call delete resource if it not contains our finalizer,
            // since the resource still can be updates when marked for deletion and contains other finalizers
            if (markedForDeletion(resource) && hasDefaultFinalizer(resource)) {
                boolean removeFinalizer = controller.deleteResource(resource, new Context(k8sClient, resourceClient));
                if (removeFinalizer) {
                    removeDefaultFinalizer(resource);
                }
            } else {
                Optional<R> updateResult = controller.createOrUpdateResource(resource, new Context<>(k8sClient, resourceClient));
                if (updateResult.isPresent()) {
                    R updatedResource = updateResult.get();
                    addFinalizerIfNotPresent(updatedResource);
                    replace(updatedResource);
                    // We always add the default finalizer if missing and not marked for deletion.
                } else if (!hasDefaultFinalizer(resource) && !markedForDeletion(resource)) {
                    addFinalizerIfNotPresent(resource);
                    replace(resource);
                }
            }
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
        resourceOperation.lockResourceVersion(resource.getMetadata().getResourceVersion()).replace(resource);
    }

    private void replace(R resource) {
        resourceOperation.lockResourceVersion(resource.getMetadata().getResourceVersion()).replace(resource);
    }

    private void addFinalizerIfNotPresent(R resource) {
        if (!hasDefaultFinalizer(resource)) {
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
