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
package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Provides useful operations to manipulate resources (server-side apply, patch, etc.) in an
 * idiomatic way, in particular to make sure that the latest version of the resource is present in
 * the caches for the next reconciliation.
 *
 * @param <P> the resource type on which this object operates
 */
public class ResourceOperations<P extends HasMetadata> {

  public static final int DEFAULT_MAX_RETRY = 10;

  private static final Logger log = LoggerFactory.getLogger(ResourceOperations.class);

  private final Context<P> context;

  public ResourceOperations(Context<P> context) {
    this.context = context;
  }

  /**
   * Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from the update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata. In case of SSA we advise not to do updates with optimistic locking.
   *
   * @param resource fresh resource for server side apply
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R serverSideApply(R resource) {
    return resourcePatch(
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()));
  }

  /**
   * Server-Side Apply the resource status subresource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata. In case of SSA we advise not to do updates with optimistic locking.
   *
   * @param resource fresh resource for server side apply
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R serverSideApplyStatus(R resource) {
    return resourcePatch(
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()));
  }

  /**
   * Server-Side Apply the primary resource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata. In case of SSA we advise not to do updates with optimistic locking.
   *
   * @param resource primary resource for server side apply
   * @return updated resource
   */
  public P serverSideApplyPrimary(P resource) {
    return resourcePatch(
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Server-Side Apply the primary resource status subresource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata. In case of SSA we advise not to do updates with optimistic locking.
   *
   * @param resource primary resource for server side apply
   * @return updated resource
   */
  public P serverSideApplyPrimaryStatus(P resource) {
    return resourcePatch(
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to update
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R update(R resource) {
    return resourcePatch(resource, r -> context.getClient().resource(r).update());
  }

  /**
   * Creates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to update
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R create(R resource) {
    return resourcePatch(resource, r -> context.getClient().resource(r).create());
  }

  /**
   * Updates the resource status subresource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to update
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R updateStatus(R resource) {
    return resourcePatch(resource, r -> context.getClient().resource(r).updateStatus());
  }

  /**
   * Updates the primary resource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource primary resource to update
   * @return updated resource
   */
  public P updatePrimary(P resource) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).update(),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Updates the primary resource status subresource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource primary resource to update
   * @return updated resource
   */
  public P updatePrimaryStatus(P resource) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).updateStatus(),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Applies a JSON Patch to the resource. The unaryOperator function is used to modify the
   * resource, and the differences are sent as a JSON Patch to the Kubernetes API server.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonPatch(R resource, UnaryOperator<R> unaryOperator) {
    return resourcePatch(resource, r -> context.getClient().resource(r).edit(unaryOperator));
  }

  /**
   * Applies a JSON Patch to the resource status subresource. The unaryOperator function is used to
   * modify the resource status, and the differences are sent as a JSON Patch.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonPatchStatus(R resource, UnaryOperator<R> unaryOperator) {
    return resourcePatch(resource, r -> context.getClient().resource(r).editStatus(unaryOperator));
  }

  /**
   * Applies a JSON Patch to the primary resource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   */
  public P jsonPatchPrimary(P resource, UnaryOperator<P> unaryOperator) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).edit(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Applies a JSON Patch to the primary resource status subresource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   */
  public P jsonPatchPrimaryStatus(P resource, UnaryOperator<P> unaryOperator) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Applies a JSON Merge Patch to the resource. JSON Merge Patch (RFC 7386) is a simpler patching
   * strategy that merges the provided resource with the existing resource on the server.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to patch
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonMergePatch(R resource) {
    return resourcePatch(resource, r -> context.getClient().resource(r).patch());
  }

  /**
   * Applies a JSON Merge Patch to the resource status subresource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource resource to patch
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonMergePatchStatus(R resource) {
    return resourcePatch(resource, r -> context.getClient().resource(r).patchStatus());
  }

  /**
   * Applies a JSON Merge Patch to the primary resource. Caches the response using the controller's
   * event source.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource primary resource to patch reconciliation
   * @return updated resource
   */
  public P jsonMergePatchPrimary(P resource) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Applies a JSON Merge Patch to the primary resource.
   *
   * <p>Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will see to updated resource - or more recent one if additional update happened
   * after this update; In addition to that it filters out the event from this update, so
   * reconciliation is not triggered by own update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata.
   *
   * @param resource primary resource to patch
   * @return updated resource
   * @see #jsonMergePatchPrimaryStatus(HasMetadata)
   */
  public P jsonMergePatchPrimaryStatus(P resource) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patchStatus(),
        context.eventSourceRetriever().getControllerEventSource());
  }

  /**
   * Utility method to patch a resource and cache the result. Automatically discovers the event
   * source for the resource type and delegates to {@link #resourcePatch(HasMetadata, UnaryOperator,
   * ManagedInformerEventSource)}.
   *
   * @param resource resource to patch
   * @param updateOperation operation to perform (update, patch, edit, etc.)
   * @return updated resource
   * @param <R> resource type
   * @throws IllegalStateException if no event source or multiple event sources are found
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public <R extends HasMetadata> R resourcePatch(R resource, UnaryOperator<R> updateOperation) {

    var esList = context.eventSourceRetriever().getEventSourcesFor(resource.getClass());
    if (esList.isEmpty()) {
      throw new IllegalStateException("No event source found for type: " + resource.getClass());
    }
    if (esList.size() > 1) {
      throw new IllegalStateException(
          "Multiple event sources found for: "
              + resource.getClass()
              + " please provide the target event source");
    }
    var es = esList.get(0);
    if (es instanceof ManagedInformerEventSource mes) {
      return resourcePatch(resource, updateOperation, (ManagedInformerEventSource<R, P, ?>) mes);
    } else {
      throw new IllegalStateException(
          "Target event source must be a subclass off "
              + ManagedInformerEventSource.class.getName());
    }
  }

  /**
   * Utility method to patch a resource and cache the result using the specified event source. This
   * method either filters out the resulting event or allows it to trigger reconciliation based on
   * the filterEvent parameter.
   *
   * @param resource resource to patch
   * @param updateOperation operation to perform (update, patch, edit, etc.)
   * @param ies the managed informer event source to use for caching
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R resourcePatch(
      R resource, UnaryOperator<R> updateOperation, ManagedInformerEventSource<R, P, ?> ies) {
    return ies.eventFilteringUpdateAndCacheResource(resource, updateOperation);
  }

  /**
   * Adds the default finalizer (from controller configuration) to the primary resource. This is a
   * convenience method that calls {@link #addFinalizer(String)} with the configured finalizer name.
   * Note that explicitly adding/removing finalizer is required only if "Trigger reconciliation on
   * all event" mode is on.
   *
   * @return updated resource from the server response
   * @see #addFinalizer(String)
   */
  public P addFinalizer() {
    return addFinalizer(context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Adds finalizer to the resource using JSON Patch. Retries conflicts and unprocessable content
   * (HTTP 422). It does not try to add finalizer if there is already a finalizer or resource is
   * marked for deletion. Note that explicitly adding/removing finalizer is required only if
   * "Trigger reconciliation on all event" mode is on.
   *
   * @return updated resource from the server response
   */
  public P addFinalizer(String finalizerName) {
    var resource = context.getPrimaryResource();
    if (resource.isMarkedForDeletion() || resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatchPrimary(
        r -> {
          r.addFinalizer(finalizerName);
          return r;
        },
        r -> !r.hasFinalizer(finalizerName));
  }

  /**
   * Removes the default finalizer (from controller configuration) from the primary resource. This
   * is a convenience method that calls {@link #removeFinalizer(String)} with the configured
   * finalizer name. Note that explicitly adding/removing finalizer is required only if "Trigger
   * reconciliation on all event" mode is on.
   *
   * @return updated resource from the server response
   * @see #removeFinalizer(String)
   */
  public P removeFinalizer() {
    return removeFinalizer(context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Removes the target finalizer from the primary resource. Uses JSON Patch and handles retries. It
   * does not try to remove finalizer if finalizer is not present on the resource. Note that
   * explicitly adding/removing finalizer is required only if "Trigger reconciliation on all event"
   * mode is on.
   *
   * @return updated resource from the server response
   */
  public P removeFinalizer(String finalizerName) {
    var resource = context.getPrimaryResource();
    if (!resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatchPrimary(
        r -> {
          r.removeFinalizer(finalizerName);
          return r;
        },
        r -> {
          if (r == null) {
            log.warn("Cannot remove finalizer since resource not exists.");
            return false;
          }
          return r.hasFinalizer(finalizerName);
        });
  }

  /**
   * Patches the resource using JSON Patch. In case the server responds with conflict (HTTP 409) or
   * unprocessable content (HTTP 422) it retries the operation up to the maximum number defined in
   * {@link ResourceOperations#DEFAULT_MAX_RETRY}.
   *
   * @param resourceChangesOperator changes to be done on the resource before update
   * @param preCondition condition to check if the patch operation still needs to be performed or
   *     not.
   * @return updated resource from the server or unchanged if the precondition does not hold.
   */
  @SuppressWarnings("unchecked")
  public P conflictRetryingPatchPrimary(
      UnaryOperator<P> resourceChangesOperator, Predicate<P> preCondition) {
    var resource = context.getPrimaryResource();
    var client = context.getClient();
    if (log.isDebugEnabled()) {
      log.debug("Conflict retrying update for: {}", ResourceID.fromResource(resource));
    }
    int retryIndex = 0;
    while (true) {
      try {
        if (!preCondition.test(resource)) {
          return resource;
        }
        return jsonPatchPrimary(resource, resourceChangesOperator);
      } catch (KubernetesClientException e) {
        log.trace("Exception during patch for resource: {}", resource);
        retryIndex++;
        // only retry on conflict (409) and unprocessable content (422) which
        // can happen if JSON Patch is not a valid request since there was
        // a concurrent request which already removed another finalizer:
        // List element removal from a list is by index in JSON Patch
        // so if addressing a second finalizer but first is meanwhile removed
        // it is a wrong request.
        if (e.getCode() != 409 && e.getCode() != 422) {
          throw e;
        }
        if (retryIndex >= DEFAULT_MAX_RETRY) {
          throw new OperatorException(
              "Exceeded maximum ("
                  + DEFAULT_MAX_RETRY
                  + ") retry attempts to patch resource: "
                  + ResourceID.fromResource(resource));
        }
        log.debug(
            "Retrying patch for resource name: {}, namespace: {}; HTTP code: {}",
            resource.getMetadata().getName(),
            resource.getMetadata().getNamespace(),
            e.getCode());
        var operation = client.resources(resource.getClass());
        if (resource.getMetadata().getNamespace() != null) {
          resource =
              (P)
                  operation
                      .inNamespace(resource.getMetadata().getNamespace())
                      .withName(resource.getMetadata().getName())
                      .get();
        } else {
          resource = (P) operation.withName(resource.getMetadata().getName()).get();
        }
      }
    }
  }

  /**
   * Adds the default finalizer (from controller configuration) to the primary resource using
   * Server-Side Apply. This is a convenience method that calls {@link #addFinalizerWithSSA(
   * String)} with the configured finalizer name. Note that explicitly adding finalizer is required
   * only if "Trigger reconciliation on all event" mode is on.
   *
   * @return the patched resource from the server response
   * @see #addFinalizerWithSSA(String)
   */
  public P addFinalizerWithSSA() {
    return addFinalizerWithSSA(context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Adds finalizer using Server-Side Apply. In the background this method creates a fresh copy of
   * the target resource, setting only name, namespace and finalizer. Does not use optimistic
   * locking for the patch. Note that explicitly adding finalizer is required only if "Trigger
   * reconciliation on all event" mode is on.
   *
   * @param finalizerName name of the finalizer to add
   * @return the patched resource from the server response
   */
  public P addFinalizerWithSSA(String finalizerName) {
    var originalResource = context.getPrimaryResource();
    if (log.isDebugEnabled()) {
      log.debug(
          "Adding finalizer (using SSA) for resource: {} version: {}",
          getUID(originalResource),
          getVersion(originalResource));
    }
    try {
      @SuppressWarnings("unchecked")
      P resource = (P) originalResource.getClass().getConstructor().newInstance();
      resource.initNameAndNamespaceFrom(originalResource);
      resource.addFinalizer(finalizerName);

      return serverSideApplyPrimary(resource);
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new RuntimeException(
          "Issue with creating custom resource instance with reflection."
              + " Custom Resources must provide a no-arg constructor. Class: "
              + originalResource.getClass().getName(),
          e);
    }
  }
}
