package io.javaoperatorsdk.operator.processing;

import io.javaoperatorsdk.operator.ControllerUtils;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
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
    private final String resourceFinalizer;
    private final CustomResourceFacade customResourceFacade;
    private final boolean generationAware;
    private final Map<String, Long> lastGenerationProcessedSuccessfully = new ConcurrentHashMap<>();

    public EventDispatcher(ResourceController controller,
                           String finalizer,
                           CustomResourceFacade customResourceFacade, boolean generationAware) {
        this.controller = controller;
        this.customResourceFacade = customResourceFacade;
        this.resourceFinalizer = finalizer;
        this.generationAware = generationAware;
    }

    public PostExecutionControl handleEvent(ExecutionScope event) {
        try {
            return handDispatch(event);
        } catch (RuntimeException e) {
            log.error("Error during event processing {} failed.", event, e);
            return PostExecutionControl.defaultDispatch();
        }
    }

    private PostExecutionControl handDispatch(ExecutionScope executionScope) {
        CustomResource resource = executionScope.getCustomResource();
        log.debug("Handling events: {} for resource {}", executionScope.getEvents(), resource.getMetadata());

        if (ProcessingUtils.containsCustomResourceDeletedEvent(executionScope.getEvents())) {
            cleanup(executionScope.getCustomResource());
            return PostExecutionControl.defaultDispatch();
        }
        if ((markedForDeletion(resource) && !ControllerUtils.hasGivenFinalizer(resource, resourceFinalizer))) {
            log.debug("Skipping event dispatching since its marked for deletion but has no finalizer: {}", executionScope);
            return PostExecutionControl.defaultDispatch();
        }
        Context context = new DefaultContext(executionScope.getCustomResource(), executionScope.getEvents());
        if (markedForDeletion(resource)) {
            return handleDelete(resource, context);
        } else {
            return handleCreateOrUpdate(executionScope, resource, context);
        }
    }

    private PostExecutionControl handleCreateOrUpdate(ExecutionScope executionScope, CustomResource resource, Context context) {
        if (!ControllerUtils.hasGivenFinalizer(resource, resourceFinalizer) && !markedForDeletion(resource)) {
            /*  We always add the finalizer if missing and not marked for deletion.
                We execute the controller processing only for processing the event sent as a results
                of the finalizer add. This will make sure that the resources are not created before
                there is a finalizer.
             */
            updateCustomResourceWithFinalizer(resource);
            return PostExecutionControl.onlyFinalizerAdded();
        } else {
            if (!skipBecauseOfGenerations(executionScope)) {
                UpdateControl<? extends CustomResource> updateControl = controller.createOrUpdateResource(resource, context);
                if (updateControl.isUpdateStatusSubResource()) {
                    customResourceFacade.updateStatus(updateControl.getCustomResource());
                } else if (updateControl.isUpdateCustomResource()) {
                    updateCustomResource(updateControl.getCustomResource());
                }
                markLastGenerationProcessed(resource);
            }
            return PostExecutionControl.defaultDispatch();
        }
    }

    private boolean skipBecauseOfGenerations(ExecutionScope executionScope) {
        if (executionScope.getEvents().size() == 1) {
            Event<?> event = executionScope.getEvents().get(0);
            if (event instanceof CustomResourceEvent) {
                Long actualGeneration = executionScope.getCustomResource().getMetadata().getGeneration();
                Long lastGeneration = lastGenerationProcessedSuccessfully.get(executionScope.getCustomResourceUid());
                if (lastGeneration == null) {
                    return false;
                }
                return actualGeneration <= lastGeneration;
            }
        }
        return false;
    }

    private PostExecutionControl handleDelete(CustomResource resource, Context context) {
        DeleteControl deleteControl = controller.deleteResource(resource, context);
        boolean hasFinalizer = ControllerUtils.hasGivenFinalizer(resource, resourceFinalizer);
        if (deleteControl.getRemoveFinalizer() && hasFinalizer) {
            removeFinalizer(resource);
            cleanup(resource);
        } else {
            log.debug("Skipping finalizer remove. removeFinalizer: {}, hasFinalizer: {} ",
                    deleteControl.getRemoveFinalizer(), hasFinalizer);
        }
        return PostExecutionControl.defaultDispatch();
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


    private void removeFinalizer(CustomResource resource) {
        log.debug("Removing finalizer on resource {}:", resource);
        resource.getMetadata().getFinalizers().remove(resourceFinalizer);
        customResourceFacade.replaceWithLock(resource);
    }

    private void replace(CustomResource resource) {
        log.debug("Trying to replace resource {}, version: {}", resource.getMetadata().getName(), resource.getMetadata().getResourceVersion());
        customResourceFacade.replaceWithLock(resource);
    }

    private void addFinalizerIfNotPresent(CustomResource resource) {
        if (!ControllerUtils.hasGivenFinalizer(resource, resourceFinalizer) && !markedForDeletion(resource)) {
            log.info("Adding finalizer to {}", resource.getMetadata());
            if (resource.getMetadata().getFinalizers() == null) {
                resource.getMetadata().setFinalizers(new ArrayList<>(1));
            }
            resource.getMetadata().getFinalizers().add(resourceFinalizer);
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
