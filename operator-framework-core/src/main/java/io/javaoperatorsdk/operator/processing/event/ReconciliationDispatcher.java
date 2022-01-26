package io.javaoperatorsdk.operator.processing.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.ObservedGenerationAware;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.Controller;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Handles calls and results of a Reconciler and finalizer related logic
 */
class ReconciliationDispatcher<R extends HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationDispatcher.class);

  private final Controller<R> controller;
  private final CustomResourceFacade<R> customResourceFacade;

  ReconciliationDispatcher(Controller<R> controller,
      CustomResourceFacade<R> customResourceFacade) {
    this.controller = controller;
    this.customResourceFacade = customResourceFacade;
  }

  public ReconciliationDispatcher(Controller<R> controller) {
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
    R resource = executionScope.getResource();
    log.debug("Handling dispatch for resource {}", getName(resource));

    final var markedForDeletion = resource.isMarkedForDeletion();
    if (markedForDeletion && shouldNotDispatchToDelete(resource)) {
      log.debug(
          "Skipping delete of resource {} because finalizer(s) {} don't allow processing yet",
          getName(resource),
          resource.getMetadata().getFinalizers());
      return PostExecutionControl.defaultDispatch();
    }

    Context context = new DefaultContext<>(executionScope.getRetryInfo(), controller, resource);
    if (markedForDeletion) {
      return handleCleanup(resource, context);
    } else {
      return handleReconcile(executionScope, resource, context);
    }
  }

  /**
   * Determines whether the given resource should be dispatched to the controller's
   * {@link Reconciler#cleanup(HasMetadata, Context)} method
   *
   * @param resource the resource to be potentially deleted
   * @return {@code true} if the resource should be handed to the controller's
   *         {@link Reconciler#cleanup(HasMetadata, Context)} method, {@code false} otherwise
   */
  private boolean shouldNotDispatchToDelete(R resource) {
    // we don't dispatch to delete if the controller is configured to use a finalizer but that
    // finalizer is not present (which means it's already been removed)
    return !configuration().useFinalizer() || (configuration().useFinalizer()
        && !resource.hasFinalizer(configuration().getFinalizer()));
  }

  private PostExecutionControl<R> handleReconcile(
      ExecutionScope<R> executionScope, R originalResource, Context context) {
    if (configuration().useFinalizer()
        && !originalResource.hasFinalizer(configuration().getFinalizer())) {
      /*
       * We always add the finalizer if missing and the controller is configured to use a finalizer.
       * We execute the controller processing only for processing the event sent as a results of the
       * finalizer add. This will make sure that the resources are not created before there is a
       * finalizer.
       */
      updateCustomResourceWithFinalizer(originalResource);
      return PostExecutionControl.onlyFinalizerAdded();
    } else {
      try {
        var resourceForExecution =
            cloneResourceForErrorStatusHandlerIfNeeded(originalResource, context);
        return reconcileExecution(executionScope, resourceForExecution, originalResource, context);
      } catch (RuntimeException e) {
        handleErrorStatusHandler(originalResource, context, e);
        throw e;
      }
    }
  }

  /**
   * Resource make sense only to clone for the ErrorStatusHandler or if the observed generation in
   * status is handled automatically. Otherwise, this operation can be skipped since it can be
   * memory and time-consuming. However, it needs to be cloned since it's common that the custom
   * resource is changed during an execution, and it's much cleaner to have to original resource in
   * place for status update.
   */
  private R cloneResourceForErrorStatusHandlerIfNeeded(R resource, Context context) {
    if (isErrorStatusHandlerPresent() ||
        shouldUpdateObservedGenerationAutomatically(resource)) {
      final var configurationService = configuration().getConfigurationService();
      return configurationService != null ? configurationService.getResourceCloner().clone(resource)
          : ConfigurationService.DEFAULT_CLONER.clone(resource);
    } else {
      return resource;
    }
  }

  private PostExecutionControl<R> reconcileExecution(ExecutionScope<R> executionScope,
      R resourceForExecution, R originalResource, Context context) {
    log.debug(
        "Reconciling resource {} with version: {} with execution scope: {}",
        getName(resourceForExecution),
        getVersion(resourceForExecution),
        executionScope);

    UpdateControl<R> updateControl = controller.reconcile(resourceForExecution, context);
    R updatedCustomResource = null;
    if (updateControl.isUpdateResourceAndStatus()) {
      updatedCustomResource = updateCustomResource(updateControl.getResource());
      updateControl
          .getResource()
          .getMetadata()
          .setResourceVersion(updatedCustomResource.getMetadata().getResourceVersion());
      updatedCustomResource = updateStatusGenerationAware(updateControl.getResource());
    } else if (updateControl.isUpdateStatus()) {
      updatedCustomResource = updateStatusGenerationAware(updateControl.getResource());
    } else if (updateControl.isUpdateResource()) {
      updatedCustomResource = updateCustomResource(updateControl.getResource());
      if (shouldUpdateObservedGenerationAutomatically(updatedCustomResource)) {
        updatedCustomResource = updateStatusGenerationAware(originalResource);
      }
    } else if (updateControl.isNoUpdate()
        && shouldUpdateObservedGenerationAutomatically(resourceForExecution)) {
      updatedCustomResource = updateStatusGenerationAware(originalResource);
    }
    return createPostExecutionControl(updatedCustomResource, updateControl);
  }

  private void handleErrorStatusHandler(R resource, Context context,
      RuntimeException e) {
    if (isErrorStatusHandlerPresent()) {
      try {
        var retryInfo = context.getRetryInfo().orElse(new RetryInfo() {
          @Override
          public int getAttemptCount() {
            return 0;
          }

          @Override
          public boolean isLastAttempt() {
            return controller.getConfiguration().getRetryConfiguration() == null;
          }
        });
        var updatedResource = ((ErrorStatusHandler<R>) controller.getReconciler())
            .updateErrorStatus(resource, retryInfo, e);
        updatedResource.ifPresent(customResourceFacade::updateStatus);
      } catch (RuntimeException ex) {
        log.error("Error during error status handling.", ex);
      }
    }
  }

  private boolean isErrorStatusHandlerPresent() {
    return controller.getReconciler() instanceof ErrorStatusHandler;
  }

  private R updateStatusGenerationAware(R resource) {
    updateStatusObservedGenerationIfRequired(resource);
    return customResourceFacade.updateStatus(resource);
  }

  private boolean shouldUpdateObservedGenerationAutomatically(R resource) {
    if (configuration().isGenerationAware() && resource instanceof CustomResource<?, ?>) {
      var customResource = (CustomResource) resource;
      var status = customResource.getStatus();
      // Note that if status is null we won't update the observed generation.
      if (status instanceof ObservedGenerationAware) {
        var observedGen = ((ObservedGenerationAware) status).getObservedGeneration();
        var currentGen = resource.getMetadata().getGeneration();
        return !currentGen.equals(observedGen);
      }
    }
    return false;
  }

  private void updateStatusObservedGenerationIfRequired(R resource) {
    if (configuration().isGenerationAware() && resource instanceof CustomResource<?, ?>) {
      var customResource = (CustomResource) resource;
      var status = customResource.getStatus();
      // Note that if status is null we won't update the observed generation.
      if (status instanceof ObservedGenerationAware) {
        ((ObservedGenerationAware) status)
            .setObservedGeneration(resource.getMetadata().getGeneration());
      }
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
    updatePostExecutionControlWithReschedule(postExecutionControl, updateControl);
    return postExecutionControl;
  }

  private void updatePostExecutionControlWithReschedule(
      PostExecutionControl<R> postExecutionControl,
      BaseControl<?> baseControl) {
    baseControl.getScheduleDelay().ifPresentOrElse(postExecutionControl::withReSchedule,
        () -> {
          var maxDelay = controller.getConfiguration().reconciliationMaxInterval();
          if (maxDelay > 0) {
            postExecutionControl.withReSchedule(controller.getConfiguration()
                .reconciliationMaxIntervalTimeUnit().toMillis(maxDelay));
          }
        });
  }

  private PostExecutionControl<R> handleCleanup(R resource, Context context) {
    log.debug(
        "Executing delete for resource: {} with version: {}",
        getName(resource),
        getVersion(resource));

    DeleteControl deleteControl = controller.cleanup(resource, context);
    final var useFinalizer = configuration().useFinalizer();
    if (useFinalizer) {
      // note that we don't reschedule here even if instructed. Removing finalizer means that
      // cleanup is finished, nothing left to done
      if (deleteControl.isRemoveFinalizer()
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
    PostExecutionControl<R> postExecutionControl = PostExecutionControl.defaultDispatch();
    updatePostExecutionControlWithReschedule(postExecutionControl, deleteControl);
    return postExecutionControl;
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

  private ControllerConfiguration<R> configuration() {
    return controller.getConfiguration();
  }

  // created to support unit testing
  static class CustomResourceFacade<R extends HasMetadata> {

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
