package io.javaoperatorsdk.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventList;

import static io.javaoperatorsdk.operator.EventListUtils.containsCustomResourceDeletedEvent;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Dispatches events to the Controller and handles Finalizers for a single type of Custom Resource.
 */
public class EventDispatcher<R extends CustomResource<?, ?>> {

  private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

  private final ConfiguredController<R> controller;
  private final CustomResourceFacade<R> customResourceFacade;

  EventDispatcher(ConfiguredController<R> controller,
      CustomResourceFacade<R> customResourceFacade) {
    this.controller = controller;
    this.customResourceFacade = customResourceFacade;
  }

  public EventDispatcher(ConfiguredController<R> controller) {
    this(controller, new CustomResourceFacade<>(controller.getCRClient()));
  }

  public PostExecutionControl<R> handleExecution(ExecutionScope<R> executionScope) {
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

  private PostExecutionControl<R> handleDispatch(ExecutionScope<R> executionScope) {
    R resource = executionScope.getCustomResource();
    log.debug("Handling events: {} for resource {}", executionScope.getEvents(), getName(resource));

    if (containsCustomResourceDeletedEvent(executionScope.getEvents())) {
      log.debug(
          "Skipping dispatch processing because of a Delete event: {} with version: {}",
          getName(resource),
          getVersion(resource));
      return PostExecutionControl.defaultDispatch();
    }

    final var markedForDeletion = resource.isMarkedForDeletion();
    if (markedForDeletion && shouldNotDispatchToDelete(resource)) {
      log.debug(
          "Skipping delete of resource {} because finalizer(s) {} don't allow processing yet",
          getName(resource),
          resource.getMetadata().getFinalizers());
      return PostExecutionControl.defaultDispatch();
    }

    Context<R> context =
        new DefaultContext<>(
            new EventList(executionScope.getEvents()), executionScope.getRetryInfo());
    if (markedForDeletion) {
      return handleDelete(resource, context);
    } else {
      return handleCreateOrUpdate(executionScope, resource, context);
    }
  }

  private ControllerConfiguration<R> configuration() {
    return controller.getConfiguration();
  }

  /**
   * Determines whether the given resource should be dispatched to the controller's
   * {@link ResourceController#deleteResource(CustomResource, Context)} method
   *
   * @param resource the resource to be potentially deleted
   * @return {@code true} if the resource should be handed to the controller's {@code
   *     deleteResource} method, {@code false} otherwise
   */
  private boolean shouldNotDispatchToDelete(R resource) {
    // we don't dispatch to delete if the controller is configured to use a finalizer but that
    // finalizer is not present (which means it's already been removed)
    return configuration().useFinalizer() && !resource.hasFinalizer(configuration().getFinalizer());
  }

  private PostExecutionControl<R> handleCreateOrUpdate(
      ExecutionScope<R> executionScope, R resource, Context<R> context) {
    if (configuration().useFinalizer() && !resource.hasFinalizer(configuration().getFinalizer())) {
      /*
       * We always add the finalizer if missing and the controller is configured to use a finalizer.
       * We execute the controller processing only for processing the event sent as a results of the
       * finalizer add. This will make sure that the resources are not created before there is a
       * finalizer.
       */
      updateCustomResourceWithFinalizer(resource);
      return PostExecutionControl.onlyFinalizerAdded();
    } else {
      log.debug(
          "Executing createOrUpdate for resource {} with version: {} with execution scope: {}",
          getName(resource),
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
      return createPostExecutionControl(updatedCustomResource, updateControl);
    }
  }

  private PostExecutionControl<R> createPostExecutionControl(R updatedCustomResource,
      UpdateControl<R> updateControl) {
    PostExecutionControl<R> postExecutionControl;
    if (updatedCustomResource != null) {
      postExecutionControl = PostExecutionControl.customResourceUpdated(updatedCustomResource);
    } else {
      postExecutionControl = PostExecutionControl.defaultDispatch();
    }
    updateControl.getReScheduleDelay().ifPresent(postExecutionControl::withReSchedule);
    return postExecutionControl;
  }

  private PostExecutionControl<R> handleDelete(R resource, Context<R> context) {
    log.debug(
        "Executing delete for resource: {} with version: {}",
        getName(resource),
        getVersion(resource));

    DeleteControl deleteControl = controller.deleteResource(resource, context);
    final var useFinalizer = configuration().useFinalizer();
    if (useFinalizer) {
      if (deleteControl == DeleteControl.DEFAULT_DELETE
          && resource.hasFinalizer(configuration().getFinalizer())) {
        R customResource = removeFinalizer(resource);
        return PostExecutionControl.customResourceUpdated(customResource);
      }
    }
    log.debug(
        "Skipping finalizer remove for resource: {} with version: {}. delete control: {}, uses finalizer: {} ",
        getUID(resource),
        getVersion(resource),
        deleteControl,
        useFinalizer);
    return PostExecutionControl.defaultDispatch();
  }

  private void updateCustomResourceWithFinalizer(R resource) {
    log.debug(
        "Adding finalizer for resource: {} version: {}", getUID(resource), getVersion(resource));
    resource.addFinalizer(configuration().getFinalizer());
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
    resource.removeFinalizer(configuration().getFinalizer());
    return customResourceFacade.replaceWithLock(resource);
  }

  private R replace(R resource) {
    log.debug(
        "Trying to replace resource {}, version: {}",
        getName(resource),
        resource.getMetadata().getResourceVersion());
    return customResourceFacade.replaceWithLock(resource);
  }

  // created to support unit testing
  static class CustomResourceFacade<R extends CustomResource<?, ?>> {

    private final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation;

    public CustomResourceFacade(
        MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation) {
      this.resourceOperation = resourceOperation;
    }

    public R updateStatus(R resource) {
      log.trace("Updating status for resource: {}", resource);
      return resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(getName(resource))
          .updateStatus(resource);
    }

    public R replaceWithLock(R resource) {
      return resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(getName(resource))
          .lockResourceVersion(resource.getMetadata().getResourceVersion())
          .replace(resource);
    }
  }
}
