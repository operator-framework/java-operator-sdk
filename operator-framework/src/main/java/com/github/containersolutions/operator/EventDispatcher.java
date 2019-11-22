package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;

public class EventDispatcher<R extends CustomResource> implements Watcher<R> {

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

    public void eventReceived(Action action, R resource) {
        try {
            log.debug("Action: {}, {}: {}, Resource: {}", action, resource.getClass().getSimpleName(),
                    resource.getMetadata().getName(), resource);
            handleEvent(action, resource);
            log.trace("Even handling finished for action: {} resource: {}", action, resource);
        } catch (RuntimeException e) {
            log.error("Error on resource: {}", resource.getMetadata().getName(), e);
        }
    }

    private void handleEvent(Action action, R resource) {
        if (action == Action.MODIFIED || action == Action.ADDED) {
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
        if (Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
        }
        if (Action.DELETED == action) {
            log.debug("Resource deleted: {}", resource.getMetadata().getName());
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

    @Override
    public void onClose(KubernetesClientException e) {
        if (e != null) {
            log.error("Error: ", e);
            // we will exit the application if there was a watching exception, because of the bug in fabric8 client
            // see https://github.com/fabric8io/kubernetes-client/issues/1318
            System.exit(1);
        }
    }
}
