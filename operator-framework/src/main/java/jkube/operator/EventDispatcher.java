package jkube.operator;

import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class EventDispatcher<R extends CustomResource,
        L extends CustomResourceList<R>,
        D extends CustomResourceDoneable<R>> implements Watcher<R> {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final CustomResourceController<R, L, D> controller;
    private final CustomResourceOperationsImpl<R, L, D> resourceOperation;
    private final String resourceDefaultFinalizer;

    public EventDispatcher(CustomResourceController<R, L, D> controller,
                           NonNamespaceOperation<R, L, D, Resource<R, D>> resourceClient) {
        this.controller = controller;
        this.resourceOperation = (CustomResourceOperationsImpl<R, L, D>) resourceClient;
        this.resourceDefaultFinalizer = ControllerUtils.getDefaultFinalizer(controller.getClass());
    }

    public void eventReceived(Action action, R resource) {
        log.debug("Action: {}, {}: {}", action, resource.getClass().getSimpleName(), resource.getMetadata().getName());

        if (action == Action.MODIFIED || action == Action.ADDED) {
            // we don't want to try delete resource if it not contains our finalizer,
            // since the resource still can be updates when marked for deletion
            if (markedForDeletion(resource) && hasDefaultFinalizer(resource)) {
                controller.deleteResource(resource);
                removeDefaultFinalizer(resource);
            } else {
                R updatedResource = controller.createOrUpdateResource(resource);
                addFinalizerIfNotPresent(updatedResource);
                replace(updatedResource);
            }
        }

        if (Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
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
        }
    }
}
