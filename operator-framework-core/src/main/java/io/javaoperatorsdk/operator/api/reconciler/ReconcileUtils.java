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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class ReconcileUtils {

  private static final Logger log = LoggerFactory.getLogger(ReconcileUtils.class);

  public static final int DEFAULT_MAX_RETRY = 10;

  private ReconcileUtils() {}

  /**
   * Server-Side Apply the resource and filters out the resulting event. This is a convenience
   * method that calls {@link #serverSideApply(Context, HasMetadata, boolean)} with filterEvent set
   * to true.
   *
   * @param context of reconciler
   * @param resource fresh resource for server side apply
   * @return updated resource
   * @param <R> resource type
   * @see #serverSideApply(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R serverSideApply(
      Context<? extends HasMetadata> context, R resource) {
    return serverSideApply(context, resource, true);
  }

  /**
   * Updates the resource and caches the response if needed, thus making sure that next
   * reconciliation will contain to updated resource. Or more recent one if someone did an update
   * after our update.
   *
   * <p>Optionally also can filter out the event, what is the result of this update.
   *
   * <p>You are free to control the optimistic locking by setting the resource version in resource
   * metadata. In case of SSA we advise not to do updates with optimistic locking.
   *
   * @param context of reconciler
   * @param resource fresh resource for server side apply
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R serverSideApply(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context,
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
        filterEvent);
  }

  /**
   * Server-Side Apply the resource status subresource and filters out the resulting event. This is
   * a convenience method that calls {@link #serverSideApplyStatus(Context, HasMetadata, boolean)}
   * with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource fresh resource for server side apply
   * @return updated resource
   * @param <R> resource type
   * @see #serverSideApplyStatus(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R serverSideApplyStatus(
      Context<? extends HasMetadata> context, R resource) {
    return serverSideApplyStatus(context, resource, true);
  }

  /**
   * Server-Side Apply the resource status subresource. Updates the resource status and caches the
   * response if needed, ensuring the next reconciliation will contain the updated resource.
   *
   * @param context of reconciler
   * @param resource fresh resource for server side apply
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R serverSideApplyStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context,
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
        filterEvent);
  }

  /**
   * Server-Side Apply the primary resource and filters out the resulting event. This is a
   * convenience method that calls {@link #serverSideApplyPrimary(Context, HasMetadata, boolean)}
   * with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource for server side apply
   * @return updated resource
   * @param <P> primary resource type
   * @see #serverSideApplyPrimary(Context, HasMetadata, boolean)
   */
  public static <P extends HasMetadata> P serverSideApplyPrimary(Context<P> context, P resource) {
    return serverSideApplyPrimary(context, resource, true);
  }

  /**
   * Server-Side Apply the primary resource. Updates the primary resource and caches the response
   * using the controller's event source, ensuring the next reconciliation will contain the updated
   * resource.
   *
   * @param context of reconciler
   * @param resource primary resource for server side apply
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P serverSideApplyPrimary(
      Context<P> context, P resource, boolean filterEvent) {
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
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Server-Side Apply the primary resource status subresource and filters out the resulting event.
   * This is a convenience method that calls {@link #serverSideApplyPrimaryStatus(Context,
   * HasMetadata, boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource for server side apply
   * @return updated resource
   * @param <P> primary resource type
   * @see #serverSideApplyPrimaryStatus(Context, HasMetadata, boolean)
   */
  public static <P extends HasMetadata> P serverSideApplyPrimaryStatus(
      Context<P> context, P resource) {
    return serverSideApplyPrimaryStatus(context, resource, true);
  }

  /**
   * Server-Side Apply the primary resource status subresource. Updates the primary resource status
   * and caches the response using the controller's event source.
   *
   * @param context of reconciler
   * @param resource primary resource for server side apply
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P serverSideApplyPrimaryStatus(
      Context<P> context, P resource, boolean filterEvent) {
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
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Updates the resource and filters out the resulting event. This is a convenience method that
   * calls {@link #update(Context, HasMetadata, boolean)} with filterEvent set to true. Uses
   * optimistic locking based on the resource version.
   *
   * @param context of reconciler
   * @param resource resource to update
   * @return updated resource
   * @param <R> resource type
   * @see #update(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R update(
      Context<? extends HasMetadata> context, R resource) {
    return update(context, resource, true);
  }

  /**
   * Updates the resource with optimistic locking based on the resource version. Caches the response
   * if needed, ensuring the next reconciliation will contain the updated resource.
   *
   * @param context of reconciler
   * @param resource resource to update
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R update(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).update(), filterEvent);
  }

  /**
   * Updates the resource status subresource and filters out the resulting event. This is a
   * convenience method that calls {@link #updateStatus(Context, HasMetadata, boolean)} with
   * filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource resource to update
   * @return updated resource
   * @param <R> resource type
   * @see #updateStatus(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R updateStatus(
      Context<? extends HasMetadata> context, R resource) {
    return updateStatus(context, resource, true);
  }

  /**
   * Updates the resource status subresource with optimistic locking. Caches the response if needed.
   *
   * @param context of reconciler
   * @param resource resource to update
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R updateStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).updateStatus(), filterEvent);
  }

  /**
   * Updates the primary resource and filters out the resulting event. This is a convenience method
   * that calls {@link #updatePrimary(Context, HasMetadata, boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource to update
   * @return updated resource
   * @param <R> resource type
   * @see #updatePrimary(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R updatePrimary(
      Context<? extends HasMetadata> context, R resource) {
    return updatePrimary(context, resource, true);
  }

  /**
   * Updates the primary resource with optimistic locking. Caches the response using the
   * controller's event source.
   *
   * @param context of reconciler
   * @param resource primary resource to update
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R updatePrimary(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).update(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Updates the primary resource status subresource and filters out the resulting event. This is a
   * convenience method that calls {@link #updatePrimaryStatus(Context, HasMetadata, boolean)} with
   * filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource to update
   * @return updated resource
   * @param <R> resource type
   * @see #updatePrimaryStatus(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R updatePrimaryStatus(
      Context<? extends HasMetadata> context, R resource) {
    return updatePrimaryStatus(context, resource, true);
  }

  /**
   * Updates the primary resource status subresource with optimistic locking. Caches the response
   * using the controller's event source.
   *
   * @param context of reconciler
   * @param resource primary resource to update
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R updatePrimaryStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).updateStatus(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Applies a JSON Patch to the resource and filters out the resulting event. This is a convenience
   * method that calls {@link #jsonPatch(Context, HasMetadata, UnaryOperator, boolean)} with
   * filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   * @param <R> resource type
   * @see #jsonPatch(Context, HasMetadata, UnaryOperator, boolean)
   */
  public static <R extends HasMetadata> R jsonPatch(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatch(context, resource, unaryOperator, true);
  }

  /**
   * Applies a JSON Patch to the resource. The unaryOperator function is used to modify the
   * resource, and the differences are sent as a JSON Patch to the Kubernetes API server.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param unaryOperator function to modify the resource
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonPatch(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).edit(unaryOperator), filterEvent);
  }

  /**
   * Applies a JSON Patch to the resource status subresource and filters out the resulting event.
   * This is a convenience method that calls {@link #jsonPatchStatus(Context, HasMetadata,
   * UnaryOperator, boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   * @param <R> resource type
   * @see #jsonPatchStatus(Context, HasMetadata, UnaryOperator, boolean)
   */
  public static <R extends HasMetadata> R jsonPatchStatus(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatchStatus(context, resource, unaryOperator, true);
  }

  /**
   * Applies a JSON Patch to the resource status subresource. The unaryOperator function is used to
   * modify the resource status, and the differences are sent as a JSON Patch.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param unaryOperator function to modify the resource
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonPatchStatus(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        context,
        resource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        filterEvent);
  }

  /**
   * Applies a JSON Patch to the primary resource and filters out the resulting event. This is a
   * convenience method that calls {@link #jsonPatchPrimary(Context, HasMetadata, UnaryOperator,
   * boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   * @param <R> resource type
   * @see #jsonPatchPrimary(Context, HasMetadata, UnaryOperator, boolean)
   */
  public static <R extends HasMetadata> R jsonPatchPrimary(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatchPrimary(context, resource, unaryOperator, true);
  }

  /**
   * Applies a JSON Patch to the primary resource. Caches the response using the controller's event
   * source.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonPatchPrimary(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).edit(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Applies a JSON Patch to the primary resource status subresource and filters out the resulting
   * event. This is a convenience method that calls {@link #jsonPatchPrimaryStatus(Context,
   * HasMetadata, UnaryOperator, boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   * @param <R> resource type
   * @see #jsonPatchPrimaryStatus(Context, HasMetadata, UnaryOperator, boolean)
   */
  public static <R extends HasMetadata> R jsonPatchPrimaryStatus(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatchPrimaryStatus(context, resource, unaryOperator, true);
  }

  /**
   * Applies a JSON Patch to the primary resource status subresource. Caches the response using the
   * controller's event source.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonPatchPrimaryStatus(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Applies a JSON Merge Patch to the resource and filters out the resulting event. This is a
   * convenience method that calls {@link #jsonMergePatch(Context, HasMetadata, boolean)} with
   * filterEvent set to true. JSON Merge Patch (RFC 7386) is a simpler patching strategy compared to
   * JSON Patch.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @return updated resource
   * @param <R> resource type
   * @see #jsonMergePatch(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R jsonMergePatch(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatch(context, resource, true);
  }

  /**
   * Applies a JSON Merge Patch to the resource. JSON Merge Patch (RFC 7386) is a simpler patching
   * strategy that merges the provided resource with the existing resource on the server.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonMergePatch(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).patch(), filterEvent);
  }

  /**
   * Applies a JSON Merge Patch to the resource status subresource and filters out the resulting
   * event. This is a convenience method that calls {@link #jsonMergePatchStatus(Context,
   * HasMetadata, boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @return updated resource
   * @param <R> resource type
   * @see #jsonMergePatchStatus(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R jsonMergePatchStatus(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatchStatus(context, resource, true);
  }

  /**
   * Applies a JSON Merge Patch to the resource status subresource. Merges the provided resource
   * status with the existing resource status on the server.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonMergePatchStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).patchStatus(), filterEvent);
  }

  /**
   * Applies a JSON Merge Patch to the primary resource and filters out the resulting event. This is
   * a convenience method that calls {@link #jsonMergePatchPrimary(Context, HasMetadata, boolean)}
   * with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @return updated resource
   * @param <R> resource type
   * @see #jsonMergePatchPrimary(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R jsonMergePatchPrimary(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatchPrimary(context, resource, true);
  }

  /**
   * Applies a JSON Merge Patch to the primary resource. Caches the response using the controller's
   * event source.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonMergePatchPrimary(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Applies a JSON Merge Patch to the primary resource status subresource and filters out the
   * resulting event. This is a convenience method that calls {@link
   * #jsonMergePatchPrimaryStatus(Context, HasMetadata, boolean)} with filterEvent set to true.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @return updated resource
   * @param <R> resource type
   * @see #jsonMergePatchPrimaryStatus(Context, HasMetadata, boolean)
   */
  public static <R extends HasMetadata> R jsonMergePatchPrimaryStatus(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatchPrimaryStatus(context, resource, true);
  }

  /**
   * Applies a JSON Merge Patch to the primary resource status subresource. Caches the response
   * using the controller's event source.
   *
   * @param context of reconciler
   * @param resource primary resource to patch
   * @param filterEvent if true the event from this update will be filtered out so won't trigger the
   *     reconciliation
   * @return updated resource
   * @param <R> resource type
   */
  public static <R extends HasMetadata> R jsonMergePatchPrimaryStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  /**
   * Internal utility method to patch a resource and cache the result. Automatically discovers the
   * event source for the resource type and delegates to {@link #resourcePatch(HasMetadata,
   * UnaryOperator, ManagedInformerEventSource, boolean)}.
   *
   * @param context of reconciler
   * @param resource resource to patch
   * @param updateOperation operation to perform (update, patch, edit, etc.)
   * @param filterEvent if true the event from this update will be filtered out
   * @return updated resource
   * @param <R> resource type
   * @throws IllegalStateException if no event source or multiple event sources are found
   */
  public static <R extends HasMetadata> R resourcePatch(
      Context<?> context, R resource, UnaryOperator<R> updateOperation, boolean filterEvent) {

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
      return resourcePatch(resource, updateOperation, mes, filterEvent);
    } else {
      throw new IllegalStateException(
          "Target event source must be a subclass off "
              + ManagedInformerEventSource.class.getName());
    }
  }

  /**
   * Internal utility method to patch a resource and cache the result using the specified event
   * source. This method either filters out the resulting event or allows it to trigger
   * reconciliation based on the filterEvent parameter.
   *
   * @param resource resource to patch
   * @param updateOperation operation to perform (update, patch, edit, etc.)
   * @param ies the managed informer event source to use for caching
   * @param filterEvent if true the event from this update will be filtered out
   * @return updated resource
   * @param <R> resource type
   */
  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> R resourcePatch(
      R resource,
      UnaryOperator<R> updateOperation,
      ManagedInformerEventSource ies,
      boolean filterEvent) {
    return filterEvent
        ? (R) ies.eventFilteringUpdateAndCacheResource(resource, updateOperation)
        : (R) ies.updateAndCacheResource(resource, updateOperation);
  }

  /**
   * Adds the default finalizer (from controller configuration) to the primary resource. This is a
   * convenience method that calls {@link #addFinalizer(Context, String)} with the configured
   * finalizer name.
   *
   * @param context of reconciler
   * @return updated resource from the server response
   * @param <P> primary resource type
   * @see #addFinalizer(Context, String)
   */
  public static <P extends HasMetadata> P addFinalizer(Context<P> context) {
    return addFinalizer(context, context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Adds finalizer to the resource using JSON Patch. Retries conflicts and unprocessable content
   * (HTTP 422), see {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient,
   * HasMetadata, UnaryOperator, Predicate)} for details on retry. It does not try to add finalizer
   * if there is already a finalizer or resource is marked for deletion.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizer(Context<P> context, String finalizerName) {
    var resource = context.getPrimaryResource();
    if (resource.isMarkedForDeletion() || resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatch(
        context,
        r -> {
          r.addFinalizer(finalizerName);
          return r;
        },
        r -> !r.hasFinalizer(finalizerName));
  }

  /**
   * Removes the default finalizer (from controller configuration) from the primary resource. This
   * is a convenience method that calls {@link #removeFinalizer(Context, String)} with the
   * configured finalizer name.
   *
   * @param context of reconciler
   * @return updated resource from the server response
   * @param <P> primary resource type
   * @see #removeFinalizer(Context, String)
   */
  public static <P extends HasMetadata> P removeFinalizer(Context<P> context) {
    return removeFinalizer(context, context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Removes the target finalizer from target resource. Uses JSON Patch and handles retries, see
   * {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient, HasMetadata,
   * UnaryOperator, Predicate)} for details. It does not try to remove finalizer if finalizer is not
   * present on the resource.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P removeFinalizer(
      Context<P> context, String finalizerName) {
    var resource = context.getPrimaryResource();
    if (!resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatch(
        context,
        r -> {
          r.removeFinalizer(finalizerName);
          return r;
        },
        r -> r.hasFinalizer(finalizerName));
  }

  /**
   * Patches the resource using JSON Patch. In case the server responds with conflict (HTTP 409) or
   * unprocessable content (HTTP 422) it retries the operation up to the maximum number defined in
   * {@link ReconcileUtils#DEFAULT_MAX_RETRY}.
   *
   * @param context reconciliation context
   * @param resourceChangesOperator changes to be done on the resource before update
   * @param preCondition condition to check if the patch operation still needs to be performed or
   *     not.
   * @return updated resource from the server or unchanged if the precondition does not hold.
   * @param <P> resource type
   */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P conflictRetryingPatch(
      Context<P> context, UnaryOperator<P> resourceChangesOperator, Predicate<P> preCondition) {
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
        return jsonPatchPrimary(context, resource, resourceChangesOperator, false);
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
   * Server-Side Apply. This is a convenience method that calls {@link #addFinalizerWithSSA(Context,
   * String)} with the configured finalizer name.
   *
   * @param context of reconciler
   * @return the patched resource from the server response
   * @param <P> primary resource type
   * @see #addFinalizerWithSSA(Context, String)
   */
  public static <P extends HasMetadata> P addFinalizerWithSSA(Context<P> context) {
    return addFinalizerWithSSA(context, context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Adds finalizer using Server-Side Apply. In the background this method creates a fresh copy of
   * the target resource, setting only name, namespace and finalizer. Does not use optimistic
   * locking for the patch.
   *
   * @param context of reconciler
   * @param finalizerName name of the finalizer to add
   * @return the patched resource from the server response
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P addFinalizerWithSSA(
      Context<P> context, String finalizerName) {
    var originalResource = context.getPrimaryResource();
    if (log.isDebugEnabled()) {
      log.debug(
          "Adding finalizer (using SSA) for resource: {} version: {}",
          getUID(originalResource),
          getVersion(originalResource));
    }
    try {
      P resource = (P) originalResource.getClass().getConstructor().newInstance();
      ObjectMeta objectMeta = new ObjectMeta();
      objectMeta.setName(originalResource.getMetadata().getName());
      objectMeta.setNamespace(originalResource.getMetadata().getNamespace());
      resource.setMetadata(objectMeta);
      resource.addFinalizer(finalizerName);

      return serverSideApplyPrimary(context, resource, false);
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

  /**
   * Compares resource versions of two resources. This is a convenience method that extracts the
   * resource versions from the metadata and delegates to {@link #compareResourceVersions(String,
   * String)}.
   *
   * @param h1 first resource
   * @param h2 second resource
   * @return negative if h1 is older, zero if equal, positive if h1 is newer
   * @throws NonComparableResourceVersionException if either resource version is invalid
   */
  public static int compareResourceVersions(HasMetadata h1, HasMetadata h2) {
    return compareResourceVersions(
        h1.getMetadata().getResourceVersion(), h2.getMetadata().getResourceVersion());
  }

  /**
   * Compares two Kubernetes resource versions numerically. Kubernetes resource versions are
   * expected to be numeric strings that increase monotonically. This method assumes both versions
   * are valid numeric strings without leading zeros.
   *
   * @param v1 first resource version
   * @param v2 second resource version
   * @return negative if v1 is older, zero if equal, positive if v1 is newer
   * @throws NonComparableResourceVersionException if either resource version is empty, has leading
   *     zeros, or contains non-numeric characters
   */
  public static int compareResourceVersions(String v1, String v2) {
    int v1Length = validateResourceVersion(v1);
    int v2Length = validateResourceVersion(v2);
    int comparison = v1Length - v2Length;
    if (comparison != 0) {
      return comparison;
    }
    for (int i = 0; i < v2Length; i++) {
      int comp = v1.charAt(i) - v2.charAt(i);
      if (comp != 0) {
        return comp;
      }
    }
    return 0;
  }

  private static int validateResourceVersion(String v1) {
    int v1Length = v1.length();
    if (v1Length == 0) {
      throw new NonComparableResourceVersionException("Resource version is empty");
    }
    for (int i = 0; i < v1Length; i++) {
      char char1 = v1.charAt(i);
      if (char1 == '0') {
        if (i == 0) {
          throw new NonComparableResourceVersionException(
              "Resource version cannot begin with 0: " + v1);
        }
      } else if (char1 < '0' || char1 > '9') {
        throw new NonComparableResourceVersionException(
            "Non numeric characters in resource version: " + v1);
      }
    }
    return v1Length;
  }
}
