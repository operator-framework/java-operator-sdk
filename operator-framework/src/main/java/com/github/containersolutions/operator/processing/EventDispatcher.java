package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Dispatches events to the Controller and handles Finalizers for a single type of Custom Resource.
 */
public class EventDispatcher {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final ResourceController controller;
    private final String resourceDefaultFinalizer;
    private final CustomResourceReplaceFacade customResourceReplaceFacade;

    public EventDispatcher(ResourceController controller,
                           String defaultFinalizer,
                           CustomResourceReplaceFacade customResourceReplaceFacade) {
        this.controller = controller;
        this.customResourceReplaceFacade = customResourceReplaceFacade;
        this.resourceDefaultFinalizer = defaultFinalizer;
    }

    public void handleEvent(Watcher.Action action, CustomResource resource) {
        log.info("Handling event {} for resource {}", action, resource.getMetadata());
        if (Watcher.Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
            return;
        }
        // Its interesting problem if we should call delete if received event after object is marked for deletion
        // but there is not our finalizer. Since it can happen that there are multiple finalizers, also other events after
        // we called delete and remove finalizers already. But also it can happen that we did not manage to put
        // finalizer into the resource before marked for delete. So for now we will call delete every time, since delete
        // operation should be idempotent too, and this way we cover the corner case.
        if (markedForDeletion(resource) || action == Watcher.Action.DELETED) {
            boolean removeFinalizer = controller.deleteResource(resource);
            if (removeFinalizer && hasDefaultFinalizer(resource)) {
                log.debug("Removing finalizer on {}: {}", resource.getMetadata().getName(), resource.getMetadata());
                removeDefaultFinalizer(resource);
            }
        } else {
            Optional<CustomResource> updateResult = controller.createOrUpdateResource(resource);
            if (updateResult.isPresent()) {
                log.debug("Updating resource: {} with version: {}", resource.getMetadata().getName(),
                        resource.getMetadata().getResourceVersion());
                log.trace("Resource before update: {}", resource);
                CustomResource updatedResource = updateResult.get();
                addFinalizerIfNotPresent(updatedResource);
                replace(updatedResource);
                log.trace("Resource after update: {}", resource);
                // We always add the default finalizer if missing and not marked for deletion.
            } else if (!hasDefaultFinalizer(resource) && !markedForDeletion(resource)) {
                log.debug("Adding finalizer for resource: {} version: {}", resource.getMetadata().getName(),
                        resource.getMetadata().getResourceVersion());
                addFinalizerIfNotPresent(resource);
                replace(resource);
            }
        }
    }

    private boolean hasDefaultFinalizer(CustomResource resource) {
        if (resource.getMetadata().getFinalizers() != null) {
            return resource.getMetadata().getFinalizers().contains(resourceDefaultFinalizer);
        }
        return false;
    }

    private void removeDefaultFinalizer(CustomResource resource) {
        resource.getMetadata().getFinalizers().remove(resourceDefaultFinalizer);
        log.debug("Removed finalizer. Trying to replace resource {}, version: {}", resource.getMetadata().getName(), resource.getMetadata().getResourceVersion());
        customResourceReplaceFacade.replaceWithLock(resource);
    }

    private void replace(CustomResource resource) {
        log.debug("Trying to replace resource {}, version: {}", resource.getMetadata().getName(), resource.getMetadata().getResourceVersion());
        customResourceReplaceFacade.replaceWithLock(resource);
    }

    private void addFinalizerIfNotPresent(CustomResource resource) {
        if (!hasDefaultFinalizer(resource) && !markedForDeletion(resource)) {
            log.info("Adding default finalizer to {}", resource.getMetadata());
            if (resource.getMetadata().getFinalizers() == null) {
                resource.getMetadata().setFinalizers(new ArrayList<>(1));
            }
            resource.getMetadata().getFinalizers().add(resourceDefaultFinalizer);
        }
    }

    private boolean markedForDeletion(CustomResource resource) {
        return resource.getMetadata().getDeletionTimestamp() != null && !resource.getMetadata().getDeletionTimestamp().isEmpty();
    }

    // created to support unit testing
    public static class CustomResourceReplaceFacade {

        private final MixedOperation<?, ?, ?, Resource<CustomResource, ?>> resourceOperation;

        public CustomResourceReplaceFacade(MixedOperation<?, ?, ?, Resource<CustomResource, ?>> resourceOperation) {
            this.resourceOperation = resourceOperation;
        }

        public CustomResource replaceWithLock(CustomResource resource) {
            resource = resourceOperation.inNamespace(resource.getMetadata().getNamespace())
                    .withName(resource.getMetadata().getName())
                    .updateStatus(resource);
            return resourceOperation.inNamespace(resource.getMetadata().getNamespace())
                    .withName(resource.getMetadata().getName())
                    .lockResourceVersion(resource.getMetadata().getResourceVersion())
                    .replace(resource);
        }
    }
}
