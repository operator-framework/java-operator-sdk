/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event;

import java.net.HttpURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.Controller;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.*;

/** Handles calls and results of a Reconciler and finalizer related logic */
class ReconciliationDispatcher<P extends HasMetadata> {

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
    this(
        controller,
        new CustomResourceFacade<>(
            controller.getConfiguration(),
            controller.getConfiguration().getConfigurationService().getResourceCloner()));
  }

  public PostExecutionControl<P> handleExecution(ExecutionScope<P> executionScope) {
    validateExecutionScope(executionScope);
    try {
      return handleDispatch(executionScope, null);
    } catch (Exception e) {
      return PostExecutionControl.exceptionDuringExecution(e);
    }
  }

  // visible for testing
  PostExecutionControl<P> handleDispatch(ExecutionScope<P> executionScope, Context<P> context)
      throws Exception {
    P originalResource = executionScope.getResource();
    var resourceForExecution = cloneResource(originalResource);
    log.debug(
        "Handling dispatch for resource name: {} namespace: {}",
        getName(originalResource),
        originalResource.getMetadata().getNamespace());

    final var markedForDeletion = originalResource.isMarkedForDeletion();
    if (!triggerOnAllEvents()
        && markedForDeletion
        && shouldNotDispatchToCleanupWhenMarkedForDeletion(originalResource)) {
      log.debug(
          "Skipping cleanup of resource {} because finalizer(s) {} don't allow processing yet",
          getName(originalResource),
          originalResource.getMetadata().getFinalizers());
      return PostExecutionControl.defaultDispatch();
    }
    // context can be provided only for testing purposes
    context =
        context == null
            ? new DefaultContext<>(
                executionScope.getRetryInfo(),
                controller,
                resourceForExecution,
                executionScope.isDeleteEvent(),
                executionScope.isDeleteFinalStateUnknown())
            : context;

    // checking the cleaner for all-event-mode
    if (!triggerOnAllEvents() && markedForDeletion) {
      return handleCleanup(resourceForExecution, context, executionScope);
    } else {
      return handleReconcile(executionScope, resourceForExecution, originalResource, context);
    }
  }

  private boolean shouldNotDispatchToCleanupWhenMarkedForDeletion(P resource) {
    var alreadyRemovedFinalizer =
        controller.useFinalizer() && !resource.hasFinalizer(configuration().getFinalizerName());
    return !controller.useFinalizer() || alreadyRemovedFinalizer;
  }

  private PostExecutionControl<P> handleReconcile(
      ExecutionScope<P> executionScope,
      P resourceForExecution,
      P originalResource,
      Context<P> context)
      throws Exception {
    if (!triggerOnAllEvents()
        && controller.useFinalizer()
        && !originalResource.hasFinalizer(configuration().getFinalizerName())) {
      /*
       * We always add the finalizer if missing and the controller is configured to use a finalizer.
       * We execute the controller processing only for processing the event sent as a results of the
       * finalizer add. This will make sure that the resources are not created before there is a
       * finalizer.
       */
      P updatedResource;
      if (useSSA) {
        updatedResource = context.resourceOperations().addFinalizerWithSSA();
      } else {
        updatedResource = context.resourceOperations().addFinalizer();
      }
      return PostExecutionControl.onlyFinalizerAdded(updatedResource)
          .withReSchedule(BaseControl.INSTANT_RESCHEDULE);
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

  private PostExecutionControl<P> reconcileExecution(
      ExecutionScope<P> executionScope,
      P resourceForExecution,
      P originalResource,
      Context<P> context)
      throws Exception {
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
        return createPostExecutionControl(null, updateControl, executionScope);
      } else {
        toUpdate = updateControl.getResource().orElseThrow();
      }
    } else {
      toUpdate =
          updateControl.isNoUpdate() ? originalResource : updateControl.getResource().orElseThrow();
    }

    if (updateControl.isPatchResource()) {
      updatedCustomResource = patchResource(context, toUpdate, originalResource);
      if (!useSSA) {
        toUpdate
            .getMetadata()
            .setResourceVersion(updatedCustomResource.getMetadata().getResourceVersion());
      }
    }

    if (updateControl.isPatchStatus()) {
      customResourceFacade.patchStatus(context, toUpdate, originalResource);
    }
    return createPostExecutionControl(updatedCustomResource, updateControl, executionScope);
  }

  private PostExecutionControl<P> handleErrorStatusHandler(
      P resource, P originalResource, Context<P> context, Exception e) throws Exception {
    RetryInfo retryInfo =
        context
            .getRetryInfo()
            .orElseGet(
                () ->
                    new RetryInfo() {
                      @Override
                      public int getAttemptCount() {
                        return 0;
                      }

                      @Override
                      public boolean isLastAttempt() {
                        // check also if the retry is limited to 0
                        return retryConfigurationHasZeroAttempts
                            || controller.getConfiguration().getRetry() == null;
                      }
                    });
    ((DefaultContext<P>) context).setRetryInfo(retryInfo);
    var errorStatusUpdateControl =
        controller.getReconciler().updateErrorStatus(resource, context, e);

    if (errorStatusUpdateControl.isDefaultErrorProcessing()) {
      throw e;
    }

    P updatedResource = null;
    if (errorStatusUpdateControl.getResource().isPresent()) {
      try {
        updatedResource =
            customResourceFacade.patchStatus(
                context, errorStatusUpdateControl.getResource().orElseThrow(), originalResource);
      } catch (Exception ex) {
        int code = ex instanceof KubernetesClientException kcex ? kcex.getCode() : -1;
        Level exceptionLevel = Level.ERROR;
        String failedMessage = "";
        if (context.isNextReconciliationImminent()
            || !(errorStatusUpdateControl.isNoRetry() || retryInfo.isLastAttempt())) {
          if (code == HttpURLConnection.HTTP_CONFLICT
              || (originalResource.getMetadata().getResourceVersion() != null && code == 422)) {
            exceptionLevel = Level.DEBUG;
            failedMessage = " due to conflict";
            log.info(
                "ErrorStatusUpdateControl.patchStatus of {} failed due to a conflict, but the next"
                    + " reconciliation is imminent.",
                ResourceID.fromResource(originalResource));
          } else {
            exceptionLevel = Level.WARN;
            failedMessage = ", but will be retried soon,";
          }
        }

        log.atLevel(exceptionLevel)
            .log(
                "ErrorStatusUpdateControl.patchStatus failed{} for {} with UID: {} and version: {}"
                    + " for error {}",
                failedMessage,
                ResourceID.fromResource(originalResource),
                getUID(resource),
                getVersion(resource),
                e.getMessage(),
                ex);
      }
    }
    if (errorStatusUpdateControl.isNoRetry()) {
      PostExecutionControl<P> postExecutionControl;
      if (updatedResource != null) {
        postExecutionControl = PostExecutionControl.customResourceStatusPatched(updatedResource);
      } else {
        postExecutionControl = PostExecutionControl.defaultDispatch();
      }
      errorStatusUpdateControl.getScheduleDelay().ifPresent(postExecutionControl::withReSchedule);
      return postExecutionControl;
    }
    throw e;
  }

  private PostExecutionControl<P> createPostExecutionControl(
      P updatedCustomResource, UpdateControl<P> updateControl, ExecutionScope<P> executionScope) {
    PostExecutionControl<P> postExecutionControl;
    if (updatedCustomResource != null) {
      postExecutionControl =
          PostExecutionControl.customResourceStatusPatched(updatedCustomResource);
    } else {
      postExecutionControl = PostExecutionControl.defaultDispatch();
    }
    updatePostExecutionControlWithReschedule(postExecutionControl, updateControl, executionScope);
    return postExecutionControl;
  }

  private void updatePostExecutionControlWithReschedule(
      PostExecutionControl<P> postExecutionControl,
      BaseControl<?> baseControl,
      ExecutionScope<P> executionScope) {
    baseControl
        .getScheduleDelay()
        .ifPresent(
            r -> {
              if (executionScope.isDeleteEvent()) {
                log.warn("No re-schedules allowed when delete event present. Will be ignored.");
              } else {
                postExecutionControl.withReSchedule(r);
              }
            });
  }

  private PostExecutionControl<P> handleCleanup(
      P resourceForExecution, Context<P> context, ExecutionScope<P> executionScope) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing delete for resource: {} with version: {}",
          ResourceID.fromResource(resourceForExecution),
          getVersion(resourceForExecution));
    }
    DeleteControl deleteControl = controller.cleanup(resourceForExecution, context);
    final var useFinalizer = controller.useFinalizer();
    if (useFinalizer && !triggerOnAllEvents()) {
      // note that we don't reschedule here even if instructed. Removing finalizer means that
      // cleanup is finished, nothing left to be done
      final var finalizerName = configuration().getFinalizerName();
      if (deleteControl.isRemoveFinalizer() && resourceForExecution.hasFinalizer(finalizerName)) {
        P customResource = context.resourceOperations().removeFinalizer();
        return PostExecutionControl.customResourceFinalizerRemoved(customResource);
      }
    }
    log.debug(
        "Skipping finalizer remove for resource: {} with version: {}. delete control: {}, uses"
            + " finalizer: {}",
        getUID(resourceForExecution),
        getVersion(resourceForExecution),
        deleteControl,
        useFinalizer);
    PostExecutionControl<P> postExecutionControl = PostExecutionControl.defaultDispatch();
    updatePostExecutionControlWithReschedule(postExecutionControl, deleteControl, executionScope);
    return postExecutionControl;
  }

  private P patchResource(Context<P> context, P resource, P originalResource) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Updating resource: {} with version: {}; SSA: {}",
          resource.getMetadata().getName(),
          getVersion(resource),
          useSSA);
    }
    log.trace("Resource before update: {}", resource);

    final var finalizerName = configuration().getFinalizerName();
    if (useSSA && controller.useFinalizer()) {
      // addFinalizer already prevents adding an already present finalizer so no need to check
      resource.addFinalizer(finalizerName);
    }
    return customResourceFacade.patchResource(context, resource, originalResource);
  }

  ControllerConfiguration<P> configuration() {
    return controller.getConfiguration();
  }

  private void validateExecutionScope(ExecutionScope<P> executionScope) {
    if (!triggerOnAllEvents()
        && (executionScope.isDeleteEvent() || executionScope.isDeleteFinalStateUnknown())) {
      throw new OperatorException(
          "isDeleteEvent or isDeleteFinalStateUnknown cannot be true if not triggerOnAllEvent."
              + " This indicates an issue with the implementation.");
    }
  }

  // created to support unit testing
  static class CustomResourceFacade<R extends HasMetadata> {

    private final boolean useSSA;
    private final Cloner cloner;

    public CustomResourceFacade(ControllerConfiguration<R> configuration, Cloner cloner) {
      this.useSSA = configuration.getConfigurationService().useSSAToPatchPrimaryResource();
      this.cloner = cloner;
    }

    public R patchResource(Context<R> context, R resource, R originalResource) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Trying to replace resource {}, version: {}",
            ResourceID.fromResource(resource),
            resource.getMetadata().getResourceVersion());
      }
      if (useSSA) {
        return context.resourceOperations().serverSideApplyPrimary(resource);
      } else {
        return context.resourceOperations().jsonPatchPrimary(originalResource, r -> resource);
      }
    }

    public R patchStatus(Context<R> context, R resource, R originalResource) {
      log.trace("Patching status for resource: {} with ssa: {}", resource, useSSA);
      if (useSSA) {
        var managedFields = resource.getMetadata().getManagedFields();
        try {
          resource.getMetadata().setManagedFields(null);
          return context.resourceOperations().serverSideApplyPrimaryStatus(resource);
        } finally {
          resource.getMetadata().setManagedFields(managedFields);
        }
      } else {
        return editStatus(context, resource, originalResource);
      }
    }

    private R editStatus(Context<R> context, R resource, R originalResource) {
      String resourceVersion = resource.getMetadata().getResourceVersion();
      // the cached resource should not be changed in any circumstances
      // that can lead to all kinds of race conditions.
      R clonedOriginal = cloner.clone(originalResource);
      try {
        clonedOriginal.getMetadata().setResourceVersion(null);
        resource.getMetadata().setResourceVersion(null);
        return context
            .resourceOperations()
            .jsonPatchPrimaryStatus(
                clonedOriginal,
                r -> {
                  ReconcilerUtilsInternal.setStatus(r, ReconcilerUtilsInternal.getStatus(resource));
                  return r;
                });
      } finally {
        // restore initial resource version
        clonedOriginal.getMetadata().setResourceVersion(resourceVersion);
        resource.getMetadata().setResourceVersion(resourceVersion);
      }
    }
  }

  private boolean triggerOnAllEvents() {
    return configuration().triggerReconcilerOnAllEvents();
  }
}
