package io.javaoperatorsdk.operator.processing.event;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.HasMetadataOperationsImpl;
import io.fabric8.kubernetes.client.utils.Serialization;
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
    } catch (Exception e) {
      log.error("Error during event processing {} failed.", executionScope, e);
      return PostExecutionControl.exceptionDuringExecution(e);
    }
  }

  private PostExecutionControl<R> handleDispatch(ExecutionScope<R> executionScope)
      throws Exception {
    R resource = executionScope.getResource();
    log.debug("Handling dispatch for resource {}", getName(resource));

    final var markedForDeletion = resource.isMarkedForDeletion();
    if (markedForDeletion && shouldNotDispatchToCleanup(resource)) {
      log.debug(
          "Skipping cleanup of resource {} because finalizer(s) {} don't allow processing yet",
          getName(resource),
          resource.getMetadata().getFinalizers());
      return PostExecutionControl.defaultDispatch();
    }

    Context<R> context = new DefaultContext<>(executionScope.getRetryInfo(), controller, resource);
    if (markedForDeletion) {
      return handleCleanup(resource, context);
    } else {
      return handleReconcile(executionScope, resource, context);
    }
  }

  private boolean shouldNotDispatchToCleanup(R resource) {
    // we don't dispatch to cleanup if the controller is configured to use a finalizer but that
    // finalizer is not present (which means it's already been removed)
    return !controller.useFinalizer() || (controller.useFinalizer()
        && !resource.hasFinalizer(configuration().getFinalizerName()));
  }

  private PostExecutionControl<R> handleReconcile(
      ExecutionScope<R> executionScope, R originalResource, Context<R> context) throws Exception {
    if (controller.useFinalizer()
        && !originalResource.hasFinalizer(configuration().getFinalizerName())) {
      /*
       * We always add the finalizer if missing and the controller is configured to use a finalizer.
       * We execute the controller processing only for processing the event sent as a results of the
       * finalizer add. This will make sure that the resources are not created before there is a
       * finalizer.
       */
      updateCustomResourceWithFinalizer(originalResource);
      return PostExecutionControl.onlyFinalizerAdded();
    } else {
      var resourceForExecution =
          cloneResource(originalResource);
      try {
        return reconcileExecution(executionScope, resourceForExecution, originalResource, context);
      } catch (Exception e) {
        return handleErrorStatusHandler(resourceForExecution, originalResource, context, e);
      }
    }
  }

  private R cloneResource(R resource) {
    final var cloner = ConfigurationServiceProvider.instance().getResourceCloner();
    return cloner.clone(resource);
  }

  private PostExecutionControl<R> reconcileExecution(ExecutionScope<R> executionScope,
      R resourceForExecution, R originalResource, Context<R> context) throws Exception {
    log.debug(
        "Reconciling resource {} with version: {} with execution scope: {}",
        getName(resourceForExecution),
        getVersion(resourceForExecution),
        executionScope);

    UpdateControl<R> updateControl = controller.reconcile(resourceForExecution, context);
    R updatedCustomResource = null;
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
  private PostExecutionControl<R> handleErrorStatusHandler(R resource, R originalResource,
      Context<R> context,
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
            return controller.getConfiguration().getRetryConfiguration() == null;
          }
        });
        ((DefaultContext<R>) context).setRetryInfo(retryInfo);
        var errorStatusUpdateControl = ((ErrorStatusHandler<R>) controller.getReconciler())
            .updateErrorStatus(resource, context, e);

        R updatedResource = null;
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

  private R updateStatusGenerationAware(R resource, R originalResource, boolean patch) {
    updateStatusObservedGenerationIfRequired(resource);
    if (patch) {
      return customResourceFacade.patchStatus(resource, originalResource);
    } else {
      return customResourceFacade.updateStatus(resource);
    }
  }

  @SuppressWarnings("rawtypes")
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

  @SuppressWarnings("rawtypes")
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
      PostExecutionControl<R> postExecutionControl,
      BaseControl<?> baseControl) {
    baseControl.getScheduleDelay().ifPresentOrElse(postExecutionControl::withReSchedule,
        () -> controller.getConfiguration().reconciliationMaxInterval()
            .ifPresent(m -> postExecutionControl.withReSchedule(m.toMillis())));
  }


  private PostExecutionControl<R> handleCleanup(R resource, Context<R> context) {
    log.debug(
        "Executing delete for resource: {} with version: {}",
        getName(resource),
        getVersion(resource));

    DeleteControl deleteControl = controller.cleanup(resource, context);
    final var useFinalizer = controller.useFinalizer();
    if (useFinalizer) {
      // note that we don't reschedule here even if instructed. Removing finalizer means that
      // cleanup is finished, nothing left to done
      if (deleteControl.isRemoveFinalizer()
          && resource.hasFinalizer(configuration().getFinalizerName())) {
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
    resource.addFinalizer(configuration().getFinalizerName());
    customResourceFacade.replaceResourceWithLock(resource);
  }

  private R updateCustomResource(R resource) {
    log.debug("Updating resource: {} with version: {}", getUID(resource), getVersion(resource));
    log.trace("Resource before update: {}", resource);
    return customResourceFacade.replaceResourceWithLock(resource);
  }

  private R removeFinalizer(R originalResource) {
    log.debug(
        "Removing finalizer on resource: {} with version: {}",
        getUID(originalResource),
        getVersion(originalResource));
    var resource = cloneResource(originalResource);
    resource.removeFinalizer(configuration().getFinalizerName());
    return customResourceFacade.patchResource(resource, originalResource);
  }


  ControllerConfiguration<R> configuration() {
    return controller.getConfiguration();
  }

  // created to support unit testing
  static class CustomResourceFacade<R extends HasMetadata> {

    private final MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation;

    public CustomResourceFacade(
        MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation) {
      this.resourceOperation = resourceOperation;
    }

    public R replaceResourceWithLock(R resource) {
      log.debug(
          "Trying to replace resource {}, version: {}",
          getName(resource),
          resource.getMetadata().getResourceVersion());
      return resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(getName(resource))
          .lockResourceVersion(resource.getMetadata().getResourceVersion())
          .replace(resource);
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

    public R patchResource(R resource, R originalResource) {
      log.trace("Patching resource: {}", resource);
      String resourceVersion = resource.getMetadata().getResourceVersion();
      // don't do optimistic locking on patch
      originalResource.getMetadata().setResourceVersion(null);
      resource.getMetadata().setResourceVersion(null);
      try (var bis = new ByteArrayInputStream(
          Serialization.asJson(originalResource).getBytes(StandardCharsets.UTF_8))) {
        return resourceOperation
            .inNamespace(resource.getMetadata().getNamespace())
            // will be simplified in fabric8 v6
            .load(bis)
            .edit(r -> resource);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      } finally {
        // restore initial resource version
        originalResource.getMetadata().setResourceVersion(resourceVersion);
        resource.getMetadata().setResourceVersion(resourceVersion);
      }
    }

    public R patchStatus(R resource, R originalResource) {
      log.trace("Updating status for resource: {}", resource);
      String resourceVersion = resource.getMetadata().getResourceVersion();
      // don't do optimistic locking on patch
      originalResource.getMetadata().setResourceVersion(null);
      resource.getMetadata().setResourceVersion(null);
      try (var bis = new ByteArrayInputStream(
          Serialization.asJson(originalResource).getBytes(StandardCharsets.UTF_8))) {
        return resourceOperation
            .inNamespace(resource.getMetadata().getNamespace())
            // will be simplified in fabric8 v6
            .load(bis)
            .editStatus(r -> resource);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      } finally {
        // restore initial resource version
        originalResource.getMetadata().setResourceVersion(resourceVersion);
        resource.getMetadata().setResourceVersion(resourceVersion);
      }
    }
  }
}
