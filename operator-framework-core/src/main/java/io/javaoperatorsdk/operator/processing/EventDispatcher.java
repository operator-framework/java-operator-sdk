package io.javaoperatorsdk.operator.processing;

import static io.javaoperatorsdk.operator.EventListUtils.containsCustomResourceDeletedEvent;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DefaultContext;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches events to the Controller and handles Finalizers for a single type of Custom Resource.
 */
class EventDispatcher<R extends CustomResource> {

  private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

  private final ResourceController<R> controller;
  private final String resourceFinalizer;
  private final CustomResourceFacade<R> customResourceFacade;

  EventDispatcher(
      ResourceController<R> controller,
      String finalizer,
      CustomResourceFacade<R> customResourceFacade) {
    this.controller = controller;
    this.customResourceFacade = customResourceFacade;
    this.resourceFinalizer = finalizer;
  }

  public EventDispatcher(
      ResourceController<R> controller,
      String finalizer,
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client) {
    this(controller, finalizer, new CustomResourceFacade<>(client));
  }

  public PostExecutionControl handleExecution(ExecutionScope<R> executionScope) {
    try {
      return handleDispatch(executionScope);
    } catch (KubernetesClientException e) {
      log.info(
          "Kubernetes exception {} {} during event processing, {} failed",
          e.getCode(),
          e.getMessage(),
          executionScope);
      return PostExecutionControl.exceptionDuringExecution(e);
    } catch (RuntimeException e) {
      log.error("Error during event processing {} failed.", executionScope, e);
      return PostExecutionControl.exceptionDuringExecution(e);
    }
  }

  private PostExecutionControl handleDispatch(ExecutionScope<R> executionScope) {
    R resource = executionScope.getCustomResource();
    log.debug(
        "Handling events: {} for resource {}", executionScope.getEvents(), resource.getMetadata());

    if (containsCustomResourceDeletedEvent(executionScope.getEvents())) {
      log.debug(
          "Skipping dispatch processing because of a Delete event: {} with version: {}",
          getUID(resource),
          getVersion(resource));
      return PostExecutionControl.defaultDispatch();
    }
    if ((resource.isMarkedForDeletion() && !resource.hasFinalizer(resourceFinalizer))) {
      log.debug(
          "Skipping event dispatching since its marked for deletion but has no finalizer: {}",
          executionScope);
      return PostExecutionControl.defaultDispatch();
    }
    Context<R> context =
        new DefaultContext<>(
            new EventList(executionScope.getEvents()), executionScope.getRetryInfo());
    if (resource.isMarkedForDeletion()) {
      return handleDelete(resource, context);
    } else {
      return handleCreateOrUpdate(executionScope, resource, context);
    }
  }

  private PostExecutionControl handleCreateOrUpdate(
      ExecutionScope<R> executionScope, R resource, Context<R> context) {
    if (!resource.hasFinalizer(resourceFinalizer)) {
      /*  We always add the finalizer if missing.
         We execute the controller processing only for processing the event sent as a results
         of the finalizer add. This will make sure that the resources are not created before
         there is a finalizer.
      */
      updateCustomResourceWithFinalizer(resource);
      return PostExecutionControl.onlyFinalizerAdded();
    } else {
      log.debug(
          "Executing createOrUpdate for resource {} with version: {} with execution scope: {}",
          getUID(resource),
          getVersion(resource),
          executionScope);
      UpdateControl<R> updateControl = controller.createOrUpdateResource(resource, context);
      R updatedCustomResource = null;
      if (updateControl.isUpdateCustomResourceAndStatusSubResource()) {
        updatedCustomResource = updateCustomResource(updateControl.getCustomResource());
        updateControl
            .getCustomResource()
            .getMetadata()
            .setResourceVersion(updatedCustomResource.getMetadata().getResourceVersion());
        updatedCustomResource =
            customResourceFacade.updateStatus(updateControl.getCustomResource());
      } else if (updateControl.isUpdateStatusSubResource()) {
        updatedCustomResource =
            customResourceFacade.updateStatus(updateControl.getCustomResource());
      } else if (updateControl.isUpdateCustomResource()) {
        updatedCustomResource = updateCustomResource(updateControl.getCustomResource());
      }

      if (updatedCustomResource != null) {
        return PostExecutionControl.customResourceUpdated(updatedCustomResource);
      } else {
        return PostExecutionControl.defaultDispatch();
      }
    }
  }

  private PostExecutionControl handleDelete(R resource, Context<R> context) {
    log.debug(
        "Executing delete for resource: {} with version: {}",
        getUID(resource),
        getVersion(resource));
    DeleteControl deleteControl = controller.deleteResource(resource, context);
    boolean hasFinalizer = resource.hasFinalizer(resourceFinalizer);
    if (deleteControl == DeleteControl.DEFAULT_DELETE && hasFinalizer) {
      R customResource = removeFinalizer(resource);
      return PostExecutionControl.customResourceUpdated(customResource);
    } else {
      log.debug(
          "Skipping finalizer remove for resource: {} with version: {}. delete control: {}, hasFinalizer: {} ",
          getUID(resource),
          getVersion(resource),
          deleteControl,
          hasFinalizer);
      return PostExecutionControl.defaultDispatch();
    }
  }

  private void updateCustomResourceWithFinalizer(R resource) {
    log.debug(
        "Adding finalizer for resource: {} version: {}", getUID(resource), getVersion(resource));
    resource.addFinalizer(resourceFinalizer);
    replace(resource);
  }

  private R updateCustomResource(R resource) {
    log.debug("Updating resource: {} with version: {}", getUID(resource), getVersion(resource));
    log.trace("Resource before update: {}", resource);
    return replace(resource);
  }

  private R removeFinalizer(R resource) {
    log.debug(
        "Removing finalizer on resource: {} with version: {}",
        getUID(resource),
        getVersion(resource));
    resource.removeFinalizer(resourceFinalizer);
    return customResourceFacade.replaceWithLock(resource);
  }

  private R replace(R resource) {
    log.debug(
        "Trying to replace resource {}, version: {}",
        resource.getMetadata().getName(),
        resource.getMetadata().getResourceVersion());
    return customResourceFacade.replaceWithLock(resource);
  }

  // created to support unit testing
  static class CustomResourceFacade<R extends CustomResource> {

    private final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation;

    public CustomResourceFacade(
        MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation) {
      this.resourceOperation = resourceOperation;
    }

    public R updateStatus(R resource) {
      log.trace("Updating status for resource: {}", resource);
      return resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(resource.getMetadata().getName())
          .updateStatus(resource);
    }

    public R replaceWithLock(R resource) {
      return resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(resource.getMetadata().getName())
          .lockResourceVersion(resource.getMetadata().getResourceVersion())
          .replace(resource);
    }
  }
}
