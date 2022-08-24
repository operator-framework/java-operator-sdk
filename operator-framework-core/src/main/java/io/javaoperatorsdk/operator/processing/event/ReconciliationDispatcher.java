package io.javaoperatorsdk.operator.processing.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.HasMetadataOperationsImpl;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.ObservedGenerationAware;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.Controller;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Handles calls and results of a Reconciler and finalizer related logic
 */
class ReconciliationDispatcher<P extends HasMetadata> {

  public static final int MAX_FINALIZER_REMOVAL_RETRY = 10;

  private static final Logger log = LoggerFactory.getLogger(ReconciliationDispatcher.class);

  private final Controller<P> controller;
  private final CustomResourceFacade<P> customResourceFacade;

  ReconciliationDispatcher(Controller<P> controller,
      CustomResourceFacade<P> customResourceFacade) {
    this.controller = controller;
    this.customResourceFacade = customResourceFacade;
  }

  public ReconciliationDispatcher(Controller<P> controller) {
    this(controller, new CustomResourceFacade<>(controller.getCRClient()));
  }

  public PostExecutionControl<P> handleExecution(ExecutionScope<P> executionScope) {
    try {
      return handleDispatch(executionScope);
    } catch (Exception e) {
      log.error("Error during event processing {} failed.", executionScope, e);
      return PostExecutionControl.exceptionDuringExecution(e);
    }
  }

  private PostExecutionControl<P> handleDispatch(ExecutionScope<P> executionScope)
      throws Exception {
    P originalResource = executionScope.getResource();
    var resourceForExecution = cloneResource(originalResource);
    log.debug("Handling dispatch for resource {}", getName(originalResource));

    final var markedForDeletion = originalResource.isMarkedForDeletion();
    if (markedForDeletion && shouldNotDispatchToCleanupWhenMarkedForDeletion(originalResource)) {
      log.debug(
          "Skipping cleanup of resource {} because finalizer(s) {} don't allow processing yet",
          getName(originalResource),
          originalResource.getMetadata().getFinalizers());
      return PostExecutionControl.defaultDispatch();
    }

    Context<P> context =
        new DefaultContext<>(executionScope.getRetryInfo(), controller, originalResource);
    if (markedForDeletion) {
      return handleCleanup(resourceForExecution, context);
    } else {
      return handleReconcile(executionScope, resourceForExecution, originalResource, context);
    }
  }

  private boolean shouldNotDispatchToCleanupWhenMarkedForDeletion(P resource) {
    var alreadyRemovedFinalizer = controller.useFinalizer()
        && !resource.hasFinalizer(configuration().getFinalizerName());
    if (alreadyRemovedFinalizer) {
      log.warn("This should not happen. Marked for deletion & already removed finalizer: {}",
          ResourceID.fromResource(resource));
    }
    return !controller.useFinalizer() || alreadyRemovedFinalizer;
  }

  private PostExecutionControl<P> handleReconcile(
      ExecutionScope<P> executionScope, P resourceForExecution, P originalResource,
      Context<P> context) throws Exception {
    if (controller.useFinalizer()
        && !originalResource.hasFinalizer(configuration().getFinalizerName())) {
      /*
       * We always add the finalizer if missing and the controller is configured to use a finalizer.
       * We execute the controller processing only for processing the event sent as a results of the
       * finalizer add. This will make sure that the resources are not created before there is a
       * finalizer.
       */
      var updatedResource = updateCustomResourceWithFinalizer(originalResource);
      return PostExecutionControl.onlyFinalizerAdded(updatedResource);
    } else {
      try {
        return reconcileExecution(executionScope, resourceForExecution, originalResource, context);
      } catch (Exception e) {
        return handleErrorStatusHandler(resourceForExecution, originalResource, context, e);
      }
    }
  }

  private P cloneResource(P resource) {
    final var cloner = ConfigurationServiceProvider.instance().getResourceCloner();
    return cloner.clone(resource);
  }

  private PostExecutionControl<P> reconcileExecution(ExecutionScope<P> executionScope,
      P resourceForExecution, P originalResource, Context<P> context) throws Exception {
    log.debug(
        "Reconciling resource {} with version: {} with execution scope: {}",
        getName(resourceForExecution),
        getVersion(resourceForExecution),
        executionScope);

    UpdateControl<P> updateControl = controller.reconcile(resourceForExecution, context);
    P updatedCustomResource = null;
    if (updateControl.isUpdateResourceAndStatus()) {
      updatedCustomResource =
          updateCustomResource(updateControl.getResource());
      updateControl
          .getResource()
          .getMetadata()
          .setResourceVersion(updatedCustomResource.getMetadata().getResourceVersion());
      updatedCustomResource =
          updateStatusGenerationAware(updateControl.getResource(), originalResource,
              updateControl.isPatch());
    } else if (updateControl.isUpdateStatus()) {
      updatedCustomResource =
          updateStatusGenerationAware(updateControl.getResource(), originalResource,
              updateControl.isPatch());
    } else if (updateControl.isUpdateResource()) {
      updatedCustomResource =
          updateCustomResource(updateControl.getResource());
      if (shouldUpdateObservedGenerationAutomatically(updatedCustomResource)) {
        updatedCustomResource =
            updateStatusGenerationAware(updateControl.getResource(), originalResource,
                updateControl.isPatch());
      }
    } else if (updateControl.isNoUpdate()
        && shouldUpdateObservedGenerationAutomatically(resourceForExecution)) {
      updatedCustomResource =
          updateStatusGenerationAware(originalResource, originalResource, updateControl.isPatch());
    }
    return createPostExecutionControl(updatedCustomResource, updateControl);
  }

  @SuppressWarnings("unchecked")
  private PostExecutionControl<P> handleErrorStatusHandler(P resource, P originalResource,
      Context<P> context,
      Exception e) throws Exception {
    if (isErrorStatusHandlerPresent()) {
      try {
        RetryInfo retryInfo = context.getRetryInfo().orElse(new RetryInfo() {
          @Override
          public int getAttemptCount() {
            return 0;
          }

          @Override
          public boolean isLastAttempt() {
            return controller.getConfiguration().getRetry() == null;
          }
        });
        ((DefaultContext<P>) context).setRetryInfo(retryInfo);
        var errorStatusUpdateControl = ((ErrorStatusHandler<P>) controller.getReconciler())
            .updateErrorStatus(resource, context, e);

        P updatedResource = null;
        if (errorStatusUpdateControl.getResource().isPresent()) {
          updatedResource = errorStatusUpdateControl.isPatch() ? customResourceFacade
              .patchStatus(errorStatusUpdateControl.getResource().orElseThrow(), originalResource)
              : customResourceFacade
                  .updateStatus(errorStatusUpdateControl.getResource().orElseThrow());
        }
        if (errorStatusUpdateControl.isNoRetry()) {
          if (updatedResource != null) {
            return errorStatusUpdateControl.isPatch()
                ? PostExecutionControl.customResourceStatusPatched(updatedResource)
                : PostExecutionControl.customResourceUpdated(updatedResource);
          } else {
            return PostExecutionControl.defaultDispatch();
          }
        }
      } catch (RuntimeException ex) {
        log.error("Error during error status handling.", ex);
      }
    }
    throw e;
  }

  private boolean isErrorStatusHandlerPresent() {
    return controller.getReconciler() instanceof ErrorStatusHandler;
  }

  private P updateStatusGenerationAware(P resource, P originalResource, boolean patch) {
    updateStatusObservedGenerationIfRequired(resource);
    if (patch) {
      return customResourceFacade.patchStatus(resource, originalResource);
    } else {
      return customResourceFacade.updateStatus(resource);
    }
  }

  @SuppressWarnings("rawtypes")
  private boolean shouldUpdateObservedGenerationAutomatically(P resource) {
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

  @SuppressWarnings("rawtypes")
  private void updateStatusObservedGenerationIfRequired(P resource) {
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

  private PostExecutionControl<P> createPostExecutionControl(P updatedCustomResource,
      UpdateControl<P> updateControl) {
    PostExecutionControl<P> postExecutionControl;
    if (updatedCustomResource != null) {
      if (updateControl.isUpdateStatus() && updateControl.isPatch()) {
        postExecutionControl =
            PostExecutionControl.customResourceStatusPatched(updatedCustomResource);
      } else {
        postExecutionControl = PostExecutionControl.customResourceUpdated(updatedCustomResource);
      }
    } else {
      postExecutionControl = PostExecutionControl.defaultDispatch();
    }
    updatePostExecutionControlWithReschedule(postExecutionControl, updateControl);
    return postExecutionControl;
  }

  private void updatePostExecutionControlWithReschedule(
      PostExecutionControl<P> postExecutionControl,
      BaseControl<?> baseControl) {
    baseControl.getScheduleDelay().ifPresentOrElse(postExecutionControl::withReSchedule,
        () -> controller.getConfiguration().maxReconciliationInterval()
            .ifPresent(m -> postExecutionControl.withReSchedule(m.toMillis())));
  }


  private PostExecutionControl<P> handleCleanup(P resource, Context<P> context) {
    log.debug(
        "Executing delete for resource: {} with version: {}",
        getName(resource),
        getVersion(resource));

    DeleteControl deleteControl = controller.cleanup(resource, context);
    final var useFinalizer = controller.useFinalizer();
    if (useFinalizer) {
      // note that we don't reschedule here even if instructed. Removing finalizer means that
      // cleanup is finished, nothing left to done
      final var finalizerName = configuration().getFinalizerName();
      if (deleteControl.isRemoveFinalizer() && resource.hasFinalizer(finalizerName)) {
        P customResource = removeFinalizer(resource, finalizerName);
        return PostExecutionControl.customResourceFinalizerRemoved(customResource);
      }
    }
    log.debug(
        "Skipping finalizer remove for resource: {} with version: {}. delete control: {}, uses finalizer: {} ",
        getUID(resource),
        getVersion(resource),
        deleteControl,
        useFinalizer);
    PostExecutionControl<P> postExecutionControl = PostExecutionControl.defaultDispatch();
    updatePostExecutionControlWithReschedule(postExecutionControl, deleteControl);
    return postExecutionControl;
  }

  private P updateCustomResourceWithFinalizer(P resource) {
    log.debug(
        "Adding finalizer for resource: {} version: {}", getUID(resource), getVersion(resource));
    resource.addFinalizer(configuration().getFinalizerName());
    return customResourceFacade.updateResource(resource);
  }

  private P updateCustomResource(P resource) {
    log.debug("Updating resource: {} with version: {}", getUID(resource), getVersion(resource));
    log.trace("Resource before update: {}", resource);
    return customResourceFacade.updateResource(resource);
  }

  ControllerConfiguration<P> configuration() {
    return controller.getConfiguration();
  }

  public P removeFinalizer(P resource, String finalizer) {
    if (log.isDebugEnabled()) {
      log.debug("Removing finalizer on resource: {}", ResourceID.fromResource(resource));
    }
    int retryIndex = 0;
    while (true) {
      try {
        var removed = resource.removeFinalizer(finalizer);
        if (!removed) {
          return resource;
        }
        return customResourceFacade.updateResource(resource);
      } catch (KubernetesClientException e) {
        log.trace("Exception during finalizer removal for resource: {}", resource);
        retryIndex++;
        // only retry on conflict (HTTP 409), otherwise fail
        if (e.getCode() != 409) {
          throw e;
        }
        if (retryIndex >= MAX_FINALIZER_REMOVAL_RETRY) {
          throw new OperatorException(
              "Exceeded maximum (" + MAX_FINALIZER_REMOVAL_RETRY
                  + ") retry attempts to remove finalizer '" + finalizer + "' for resource "
                  + ResourceID.fromResource(resource));
        }
        resource = customResourceFacade.getResource(resource.getMetadata().getNamespace(),
            resource.getMetadata().getName());
      }
    }
  }

  // created to support unit testing
  static class CustomResourceFacade<R extends HasMetadata> {

    private final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation;

    public CustomResourceFacade(
        MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation) {
      this.resourceOperation = resourceOperation;
    }

    public R getResource(String namespace, String name) {
      return resourceOperation.inNamespace(namespace).withName(name).get();
    }

    public R updateResource(R resource) {
      log.debug(
          "Trying to replace resource {}, version: {}",
          getName(resource),
          resource.getMetadata().getResourceVersion());
      return resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .resource(resource)
          .lockResourceVersion(resource.getMetadata().getResourceVersion())
          .replace();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public R updateStatus(R resource) {
      log.trace("Updating status for resource: {}", resource);
      HasMetadataOperationsImpl hasMetadataOperation = (HasMetadataOperationsImpl) resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(getName(resource))
          .lockResourceVersion(resource.getMetadata().getResourceVersion());
      return (R) hasMetadataOperation.replaceStatus(resource);
    }

    public R patchStatus(R resource, R originalResource) {
      log.trace("Updating status for resource: {}", resource);
      String resourceVersion = resource.getMetadata().getResourceVersion();
      // don't do optimistic locking on patch
      originalResource.getMetadata().setResourceVersion(null);
      resource.getMetadata().setResourceVersion(null);
      try {
        return resourceOperation
            .inNamespace(resource.getMetadata().getNamespace())
            .resource(originalResource)
            .editStatus(r -> resource);
      } finally {
        // restore initial resource version
        originalResource.getMetadata().setResourceVersion(resourceVersion);
        resource.getMetadata().setResourceVersion(resourceVersion);
      }
    }
  }
}
