package io.javaoperatorsdk.operator.processing.event;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.Controller;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.*;

/**
 * Handles calls and results of a Reconciler and finalizer related logic
 */
class ReconciliationDispatcher<P extends HasMetadata> {

  public static final int MAX_UPDATE_RETRY = 10;

  private static final Logger log = LoggerFactory.getLogger(ReconciliationDispatcher.class);

  private final Controller<P> controller;
  private final CustomResourceFacade<P> customResourceFacade;
  // this is to handle corner case, when there is a retry, but it is actually limited to 0.
  // Usually for testing purposes.
  private final boolean retryConfigurationHasZeroAttempts;
  private final Cloner cloner;
  private final boolean useSSA;

  ReconciliationDispatcher(Controller<P> controller, CustomResourceFacade<P> customResourceFacade) {
    this.controller = controller;
    this.customResourceFacade = customResourceFacade;
    final var configuration = controller.getConfiguration();
    this.cloner = configuration.getConfigurationService().getResourceCloner();

    var retry = configuration.getRetry();
    retryConfigurationHasZeroAttempts = retry == null || retry.initExecution().isLastAttempt();
    useSSA = configuration.getConfigurationService().useSSAToPatchPrimaryResource();
  }

  public ReconciliationDispatcher(Controller<P> controller) {
    this(controller,
        new CustomResourceFacade<>(controller.getCRClient(), controller.getConfiguration()));
  }

  public PostExecutionControl<P> handleExecution(ExecutionScope<P> executionScope) {
    try {
      return handleDispatch(executionScope);
    } catch (Exception e) {
      return PostExecutionControl.exceptionDuringExecution(e);
    }
  }

  private PostExecutionControl<P> handleDispatch(ExecutionScope<P> executionScope)
      throws Exception {
    P originalResource = executionScope.getResource();
    var resourceForExecution = cloneResource(originalResource);
    log.debug("Handling dispatch for resource name: {} namespace: {}", getName(originalResource),
        originalResource.getMetadata().getNamespace());

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
      return handleCleanup(resourceForExecution, originalResource, context);
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
      P updatedResource;
      if (useSSA) {
        updatedResource = addFinalizerWithSSA(originalResource);
      } else {
        updatedResource =
            updateCustomResourceWithFinalizer(resourceForExecution, originalResource);
      }
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

    final P toUpdate;
    P updatedCustomResource = null;
    if (useSSA) {
      if (updateControl.isNoUpdate()) {
        return createPostExecutionControl(null, updateControl);
      } else {
        toUpdate = updateControl.getResource().orElseThrow();
      }
    } else {
      toUpdate =
          updateControl.isNoUpdate() ? originalResource : updateControl.getResource().orElseThrow();
    }

    if (updateControl.isPatchResource()) {
      updatedCustomResource = patchResource(toUpdate, originalResource);
      if (!useSSA) {
        toUpdate.getMetadata()
            .setResourceVersion(updatedCustomResource.getMetadata().getResourceVersion());
      }
    }

    if (updateControl.isPatchStatus()) {
      customResourceFacade.patchStatus(toUpdate, originalResource);
    }
    return createPostExecutionControl(updatedCustomResource, updateControl);
  }

  @SuppressWarnings("unchecked")
  private PostExecutionControl<P> handleErrorStatusHandler(P resource, P originalResource,
      Context<P> context,
      Exception e) throws Exception {
    if (isErrorStatusHandlerPresent()) {
      try {
        RetryInfo retryInfo = context.getRetryInfo().orElseGet(() -> new RetryInfo() {
          @Override
          public int getAttemptCount() {
            return 0;
          }

          @Override
          public boolean isLastAttempt() {
            // check also if the retry is limited to 0
            return retryConfigurationHasZeroAttempts ||
                controller.getConfiguration().getRetry() == null;
          }
        });
        ((DefaultContext<P>) context).setRetryInfo(retryInfo);
        var errorStatusUpdateControl = ((ErrorStatusHandler<P>) controller.getReconciler())
            .updateErrorStatus(resource, context, e);

        P updatedResource = null;
        if (errorStatusUpdateControl.getResource().isPresent()) {
          updatedResource = customResourceFacade
              .patchStatus(errorStatusUpdateControl.getResource().orElseThrow(), originalResource);
        }
        if (errorStatusUpdateControl.isNoRetry()) {
          PostExecutionControl<P> postExecutionControl;
          if (updatedResource != null) {
            postExecutionControl =
                PostExecutionControl.customResourceStatusPatched(updatedResource);
          } else {
            postExecutionControl = PostExecutionControl.defaultDispatch();
          }
          errorStatusUpdateControl.getScheduleDelay()
              .ifPresent(postExecutionControl::withReSchedule);
          return postExecutionControl;
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

  private P patchStatusGenerationAware(P resource, P originalResource) {
    return customResourceFacade.patchStatus(resource, originalResource);
  }

  private PostExecutionControl<P> createPostExecutionControl(P updatedCustomResource,
      UpdateControl<P> updateControl) {
    PostExecutionControl<P> postExecutionControl;
    if (updatedCustomResource != null) {
      postExecutionControl =
          PostExecutionControl.customResourceStatusPatched(updatedCustomResource);
    } else {
      postExecutionControl = PostExecutionControl.defaultDispatch();
    }
    updatePostExecutionControlWithReschedule(postExecutionControl, updateControl);
    return postExecutionControl;
  }

  private void updatePostExecutionControlWithReschedule(
      PostExecutionControl<P> postExecutionControl,
      BaseControl<?> baseControl) {
    baseControl.getScheduleDelay().ifPresent(postExecutionControl::withReSchedule);
  }

  private PostExecutionControl<P> handleCleanup(P resource,
      P originalResource, Context<P> context) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing delete for resource: {} with version: {}",
          ResourceID.fromResource(resource),
          getVersion(resource));
    }
    DeleteControl deleteControl = controller.cleanup(resource, context);
    final var useFinalizer = controller.useFinalizer();
    if (useFinalizer) {
      // note that we don't reschedule here even if instructed. Removing finalizer means that
      // cleanup is finished, nothing left to done
      final var finalizerName = configuration().getFinalizerName();
      if (deleteControl.isRemoveFinalizer() && resource.hasFinalizer(finalizerName)) {
        P customResource = conflictRetryingPatch(resource, originalResource, r -> {
          // the operator might not be allowed to retrieve the resource on a retry, e.g. when its
          // permissions are removed by deleting the namespace concurrently
          if (r == null) {
            log.warn(
                "Could not remove finalizer on null resource: {} with version: {}",
                getUID(resource),
                getVersion(resource));
            return false;
          }
          return r.removeFinalizer(finalizerName);
        }, true);
        return PostExecutionControl.customResourceFinalizerRemoved(customResource);
      }
    }
    log.debug(
        "Skipping finalizer remove for resource: {} with version: {}. delete control: {}, uses finalizer: {}",
        getUID(resource),
        getVersion(resource),
        deleteControl,
        useFinalizer);
    PostExecutionControl<P> postExecutionControl = PostExecutionControl.defaultDispatch();
    updatePostExecutionControlWithReschedule(postExecutionControl, deleteControl);
    return postExecutionControl;
  }

  @SuppressWarnings("unchecked")
  private P addFinalizerWithSSA(P originalResource) {
    log.debug(
        "Adding finalizer (using SSA) for resource: {} version: {}",
        getUID(originalResource), getVersion(originalResource));
    try {
      P resource = (P) originalResource.getClass().getConstructor().newInstance();
      ObjectMeta objectMeta = new ObjectMeta();
      objectMeta.setName(originalResource.getMetadata().getName());
      objectMeta.setNamespace(originalResource.getMetadata().getNamespace());
      resource.setMetadata(objectMeta);
      resource.addFinalizer(configuration().getFinalizerName());
      return customResourceFacade.patchResourceWithSSA(resource);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
        | NoSuchMethodException e) {
      throw new RuntimeException("Issue with creating custom resource instance with reflection." +
          " Custom Resources must provide a no-arg constructor. Class: "
          + originalResource.getClass().getName(),
          e);
    }
  }

  private P updateCustomResourceWithFinalizer(P resourceForExecution, P originalResource) {
    log.debug(
        "Adding finalizer for resource: {} version: {}", getUID(originalResource),
        getVersion(originalResource));
    return conflictRetryingPatch(resourceForExecution, originalResource,
        r -> r.addFinalizer(configuration().getFinalizerName()), false);
  }

  private P patchResource(P resource, P originalResource) {
    log.debug("Updating resource: {} with version: {}", getUID(resource),
        getVersion(resource));
    log.trace("Resource before update: {}", resource);

    final var finalizerName = configuration().getFinalizerName();
    if (useSSA && controller.useFinalizer()) {
      // addFinalizer already prevents adding an already present finalizer so no need to check
      resource.addFinalizer(finalizerName);
    }
    return customResourceFacade.patchResource(resource, originalResource);
  }

  ControllerConfiguration<P> configuration() {
    return controller.getConfiguration();
  }

  public P conflictRetryingPatch(P resource, P originalResource,
      Function<P, Boolean> modificationFunction, boolean forceNotUseSSA) {
    if (log.isDebugEnabled()) {
      log.debug("Conflict retrying update for: {}", ResourceID.fromResource(resource));
    }
    int retryIndex = 0;
    while (true) {
      try {
        var modified = modificationFunction.apply(resource);
        if (Boolean.FALSE.equals(modified)) {
          return resource;
        }
        if (forceNotUseSSA) {
          return customResourceFacade.patchResourceWithoutSSA(resource, originalResource);
        } else {
          return customResourceFacade.patchResource(resource, originalResource);
        }
      } catch (KubernetesClientException e) {
        log.trace("Exception during patch for resource: {}", resource);
        retryIndex++;
        // only retry on conflict (HTTP 409), otherwise fail
        if (e.getCode() != 409) {
          throw e;
        }
        if (retryIndex >= MAX_UPDATE_RETRY) {
          throw new OperatorException(
              "Exceeded maximum (" + MAX_UPDATE_RETRY
                  + ") retry attempts to patch resource: "
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
    private final boolean useSSA;
    private final String fieldManager;

    public CustomResourceFacade(
        MixedOperation<R, KubernetesResourceList<R>, Resource<R>> resourceOperation,
        ControllerConfiguration<R> configuration) {
      this.resourceOperation = resourceOperation;
      this.useSSA =
          configuration.getConfigurationService().useSSAToPatchPrimaryResource();
      this.fieldManager = configuration.fieldManager();
    }

    public R getResource(String namespace, String name) {
      if (namespace != null) {
        return resourceOperation.inNamespace(namespace).withName(name).get();
      } else {
        return resourceOperation.withName(name).get();
      }
    }

    public R patchResourceWithoutSSA(R resource, R originalResource) {
      return resource(originalResource).edit(r -> resource);
    }

    public R patchResource(R resource, R originalResource) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Trying to replace resource {}, version: {}",
            ResourceID.fromResource(resource),
            resource.getMetadata().getResourceVersion());
      }
      if (useSSA) {
        return patchResourceWithSSA(resource);
      } else {
        return resource(originalResource).edit(r -> resource);
      }
    }

    public R patchStatus(R resource, R originalResource) {
      log.trace("Patching status for resource: {} with ssa: {}", resource, useSSA);
      String resourceVersion = resource.getMetadata().getResourceVersion();
      originalResource.getMetadata().setResourceVersion(null);
      resource.getMetadata().setResourceVersion(null);
      try {
        if (useSSA) {
          var managedFields = resource.getMetadata().getManagedFields();
          try {
            resource.getMetadata().setManagedFields(null);
            var res = resource(resource);
            return res.subresource("status").patch(new PatchContext.Builder()
                .withFieldManager(fieldManager)
                .withForce(true)
                .withPatchType(PatchType.SERVER_SIDE_APPLY)
                .build());
          } finally {
            resource.getMetadata().setManagedFields(managedFields);
          }
        } else {
          var res = resource(originalResource);
          return res.editStatus(r -> resource);
        }
      } finally {
        // restore initial resource version
        originalResource.getMetadata().setResourceVersion(resourceVersion);
        resource.getMetadata().setResourceVersion(resourceVersion);
      }
    }

    public R patchResourceWithSSA(R resource) {
      return resource(resource).patch(new PatchContext.Builder()
          .withFieldManager(fieldManager)
          .withForce(true)
          .withPatchType(PatchType.SERVER_SIDE_APPLY)
          .build());
    }

    private Resource<R> resource(R resource) {
      return resource instanceof Namespaced ? resourceOperation
          .inNamespace(resource.getMetadata().getNamespace())
          .resource(resource) : resourceOperation.resource(resource);
    }
  }
}
