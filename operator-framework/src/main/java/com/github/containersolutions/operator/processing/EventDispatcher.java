package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.ControllerUtils;
import com.github.containersolutions.operator.api.*;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches events to the Controller and handles Finalizers for a single type of Custom Resource.
 */
public class EventDispatcher {

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final ResourceController controller;
    private final String resourceDefaultFinalizer;
    private final CustomResourceFacade customResourceFacade;
    private final boolean generationAware;
    private final Map<String, Long> lastGenerationProcessedSuccessfully = new ConcurrentHashMap<>();

    public EventDispatcher(ResourceController controller,
                           String defaultFinalizer,
                           CustomResourceFacade customResourceFacade, boolean generationAware) {
        this.controller = controller;
        this.customResourceFacade = customResourceFacade;
        this.resourceDefaultFinalizer = defaultFinalizer;
        this.generationAware = generationAware;
    }

    public void handleEvent(CustomResourceEvent event) {
        Watcher.Action action = event.getAction();
        CustomResource resource = event.getResource();
        log.info("Handling {} event for resource {}", action, resource.getMetadata());
        if (Watcher.Action.ERROR == action) {
            log.error("Received error for resource: {}", resource.getMetadata().getName());
            return;
        }
        if (markedForDeletion(resource) && !ControllerUtils.hasDefaultFinalizer(resource, resourceDefaultFinalizer)) {
            log.debug("Skipping event dispatching since its marked for deletion but has no default finalizer: {}", event);
            return;
        }
        Context context = new DefaultContext(new RetryInfo(event.getRetryCount(), event.getRetryExecution().isLastExecution()));
        if (markedForDeletion(resource)) {
            boolean removeFinalizer = controller.deleteResource(resource, context);
            boolean hasDefaultFinalizer = ControllerUtils.hasDefaultFinalizer(resource, resourceDefaultFinalizer);
            if (removeFinalizer && hasDefaultFinalizer) {
                removeDefaultFinalizer(resource);
            } else {
                log.debug("Skipping finalizer remove. removeFinalizer: {}, hasDefaultFinalizer: {} ",
                        removeFinalizer, hasDefaultFinalizer);
            }
            cleanup(resource);
        } else {
            if (!ControllerUtils.hasDefaultFinalizer(resource, resourceDefaultFinalizer) && !markedForDeletion(resource)) {
                /*  We always add the default finalizer if missing and not marked for deletion.
                    We execute the controller processing only for processing the event sent as a results
                    of the finalizer add. This will make sure that the resources are not created before
                    there is a finalizer.
                 */
                updateCustomResourceWithFinalizer(resource);
            } else {
                if (!generationAware || largerGenerationThenProcessedBefore(resource)) {
                    UpdateControl<? extends CustomResource> updateControl = controller.createOrUpdateResource(resource, context);
                    if (updateControl.isUpdateStatusSubResource()) {
                        customResourceFacade.updateStatus(updateControl.getCustomResource());
                    } else if (updateControl.isUpdateCustomResource()) {
                        updateCustomResource(updateControl.getCustomResource());
                    }
                    markLastGenerationProcessed(resource);
                } else {
                    log.debug("Skipping processing since generation not increased. Event: {}", event);
                }
            }
        }
    }

    public boolean largerGenerationThenProcessedBefore(CustomResource resource) {
        Long lastGeneration = lastGenerationProcessedSuccessfully.get(resource.getMetadata().getUid());
        if (lastGeneration == null) {
            return true;
        } else {
            return resource.getMetadata().getGeneration() > lastGeneration;
        }
    }

    private void cleanup(CustomResource resource) {
        if (generationAware) {
            lastGenerationProcessedSuccessfully.remove(resource.getMetadata().getUid());
        }
    }

    private void markLastGenerationProcessed(CustomResource resource) {
        if (generationAware) {
            lastGenerationProcessedSuccessfully.put(resource.getMetadata().getUid(), resource.getMetadata().getGeneration());
        }
    }

    private void updateCustomResourceWithFinalizer(CustomResource resource) {
        log.debug("Adding finalizer for resource: {} version: {}", resource.getMetadata().getName(),
                resource.getMetadata().getResourceVersion());
        addFinalizerIfNotPresent(resource);
        replace(resource);
    }

    private void updateCustomResource(CustomResource updatedResource) {
        log.debug("Updating resource: {} with version: {}", updatedResource.getMetadata().getName(),
                updatedResource.getMetadata().getResourceVersion());
        log.trace("Resource before update: {}", updatedResource);
        replace(updatedResource);
    }


    private void removeDefaultFinalizer(CustomResource resource) {
        log.debug("Removing finalizer on resource {}:", resource);
        resource.getMetadata().getFinalizers().remove(resourceDefaultFinalizer);
        customResourceFacade.replaceWithLock(resource);
    }

    private void replace(CustomResource resource) {
        log.debug("Trying to replace resource {}, version: {}", resource.getMetadata().getName(), resource.getMetadata().getResourceVersion());
        customResourceFacade.replaceWithLock(resource);
    }

    private void addFinalizerIfNotPresent(CustomResource resource) {
        if (!ControllerUtils.hasDefaultFinalizer(resource, resourceDefaultFinalizer) && !markedForDeletion(resource)) {
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
    public static class CustomResourceFacade {

        private final MixedOperation<?, ?, ?, Resource<CustomResource, ?>> resourceOperation;

        public CustomResourceFacade(MixedOperation<?, ?, ?, Resource<CustomResource, ?>> resourceOperation) {
            this.resourceOperation = resourceOperation;
        }

        public void updateStatus(CustomResource resource) {
            log.trace("Updating status for resource: {}", resource);
            resourceOperation.inNamespace(resource.getMetadata().getNamespace())
                    .withName(resource.getMetadata().getName())
                    .updateStatus(resource);
        }

        public CustomResource replaceWithLock(CustomResource resource) {
            return resourceOperation.inNamespace(resource.getMetadata().getNamespace())
                    .withName(resource.getMetadata().getName())
                    .lockResourceVersion(resource.getMetadata().getResourceVersion())
                    .replace(resource);
        }
    }
}
