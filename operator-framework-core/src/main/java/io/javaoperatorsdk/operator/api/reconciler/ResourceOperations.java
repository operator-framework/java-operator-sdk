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
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.matcher.Matcher;
import io.javaoperatorsdk.operator.api.reconciler.matcher.UpdateType;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Provides useful operations to manipulate resources (server-side apply, patch, etc.) in an
 * idiomatic way, in particular to make sure that the latest version of the resource is present in
 * the caches for the next reconciliation. In other words, it provides read-cache-after-write
 * consistency.
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
    return serverSideApply(resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA));
  }

  @Experimental(API_MIGHT_CHANGE)
  public <R extends HasMetadata> R serverSideApply(R resource, Options options) {
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
        options);
  }

  public <R extends HasMetadata> R serverSideApply(
      R resource, InformerEventSource<R, P> informerEventSource) {
    return serverSideApply(resource, informerEventSource, null);
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
   * @param informerEventSource InformerEventSource to use for resource caching and filtering
   * @param <R> resource type
   */
  @Experimental(API_MIGHT_CHANGE)
  public <R extends HasMetadata> R serverSideApply(
      R resource, InformerEventSource<R, P> informerEventSource, Options options) {
    if (informerEventSource == null) {
      return serverSideApply(resource);
    }
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
        informerEventSource,
        options);
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
  public <R extends HasMetadata> R serverSideApplyStatus(R resource, Options options) {
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
        options);
  }

  public <R extends HasMetadata> R serverSideApplyStatus(R resource) {
    return serverSideApplyStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA_STATUS));
  }

  public P serverSideApplyPrimary(P resource) {
    return serverSideApplyPrimary(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA));
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
  public P serverSideApplyPrimary(P resource, Options options) {
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
        options);
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
   * @param desired primary resource for server side apply
   * @return updated resource
   */
  public P serverSideApplyPrimaryStatus(P desired) {
    return serverSideApplyPrimaryStatus(
        desired, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA_STATUS));
  }

  public P serverSideApplyPrimaryStatus(P desired, Options options) {
    return resourcePatch(
        desired,
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
        options);
  }

  public <R extends HasMetadata> R update(R resource) {
    return update(resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.UPDATE));
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
   * @param desired resource to update
   * @return updated resource
   * @param <R> resource type
   */
  @Experimental(API_MIGHT_CHANGE)
  public <R extends HasMetadata> R update(R desired, Options options) {
    return resourcePatch(desired, r -> context.getClient().resource(r).update(), options);
  }

  public <R extends HasMetadata> R update(
      R resource, InformerEventSource<R, P> informerEventSource) {
    return update(
        resource, informerEventSource, Options.matchAndFilterWithDefaultMatcher(UpdateType.UPDATE));
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
   * @param informerEventSource InformerEventSource to use for resource caching and filtering
   * @param <R> resource type
   */
  @Experimental(API_MIGHT_CHANGE)
  public <R extends HasMetadata> R update(
      R resource, InformerEventSource<R, P> informerEventSource, Options options) {
    if (informerEventSource == null) {
      return update(resource);
    }
    return resourcePatch(
        resource, r -> context.getClient().resource(r).update(), informerEventSource, options);
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
    // it is safe to do event filtering for create since check if the resource already exists.
    return create(resource, Options.forceFilterEvents());
  }

  public <R extends HasMetadata> R create(R resource, Options options) {
    if (options.getMatcher().isPresent()) {
      throw new IllegalArgumentException(
          "Create operation does not support matcher. There is nothing to match.");
    }
    return resourcePatch(resource, r -> context.getClient().resource(r).create(), options);
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
   * @param informerEventSource InformerEventSource to use for resource caching and filtering
   * @param <R> resource type
   */
  public <R extends HasMetadata> R create(
      R resource, InformerEventSource<R, P> informerEventSource, Options options) {
    if (informerEventSource == null) {
      return create(resource);
    }
    // it is safe to do event filtering for create since check if the resource already exists.
    return resourcePatch(
        resource, r -> context.getClient().resource(r).create(), informerEventSource, options);
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
    return updateStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.UPDATE_STATUS));
  }

  public <R extends HasMetadata> R updateStatus(R resource, Options options) {
    return resourcePatch(resource, r -> context.getClient().resource(r).updateStatus(), options);
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
   * @param desired primary resource to update
   * @return updated resource
   */
  public P updatePrimary(P desired) {
    return updatePrimary(desired, Options.matchAndFilterWithDefaultMatcher(UpdateType.UPDATE));
  }

  public P updatePrimary(P desired, Options options) {
    return resourcePatch(
        desired,
        r -> context.getClient().resource(r).update(),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
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
   * @param desired primary resource to update
   * @return updated resource
   */
  public P updatePrimaryStatus(P desired) {
    return resourcePatch(
        desired,
        r -> context.getClient().resource(r).updateStatus(),
        context.eventSourceRetriever().getControllerEventSource(),
        Options.matchAndFilterWithDefaultMatcher(UpdateType.UPDATE_STATUS));
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
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonPatch(R actualResource, UnaryOperator<R> unaryOperator) {
    return jsonPatch(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH));
  }

  public <R extends HasMetadata> R jsonPatch(
      R actualResource, UnaryOperator<R> unaryOperator, Options options) {
    return resourcePatch(
        actualResource,
        r -> context.getClient().resource(actualResource).edit(unaryOperator),
        options);
  }

  public <R extends HasMetadata> R jsonPatch(
      R actualResource,
      UnaryOperator<R> unaryOperator,
      InformerEventSource<R, P> informerEventSource,
      Options options) {

    return resourcePatch(
        actualResource,
        r -> context.getClient().resource(actualResource).edit(unaryOperator),
        informerEventSource,
        options);
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
   * @param actualResource resource to patch
   * @param unaryOperator function to modify the resource status
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonPatchStatus(
      R actualResource, UnaryOperator<R> unaryOperator) {
    return jsonPatchStatus(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH_STATUS));
  }

  public <R extends HasMetadata> R jsonPatchStatus(
      R actualResource, UnaryOperator<R> unaryOperator, Options options) {
    R desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        options);
  }

  public <R extends HasMetadata> R jsonPatchStatus(
      R actualResource,
      UnaryOperator<R> unaryOperator,
      InformerEventSource<R, P> informerEventSource,
      Options options) {
    R desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        informerEventSource,
        options);
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
   * @param actualResource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   */
  public P jsonPatchPrimary(P actualResource, UnaryOperator<P> unaryOperator) {
    return jsonPatchPrimary(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH));
  }

  public P jsonPatchPrimary(P actualResource, UnaryOperator<P> unaryOperator, Options options) {
    P desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).edit(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
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
   * @param actualResource primary resource to patch
   * @param unaryOperator function to modify the resource
   * @return updated resource
   */
  public P jsonPatchPrimaryStatus(P actualResource, UnaryOperator<P> unaryOperator) {
    return jsonPatchPrimaryStatus(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH_STATUS));
  }

  public P jsonPatchPrimaryStatus(
      P actualResource, UnaryOperator<P> unaryOperator, Options options) {
    P desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).editStatus(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
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
   * @param desired resource to patch
   * @return updated resource
   * @param <R> resource type
   */
  public <R extends HasMetadata> R jsonMergePatch(R desired) {
    return jsonMergePatch(
        desired, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH));
  }

  public <R extends HasMetadata> R jsonMergePatch(R desired, Options options) {
    return resourcePatch(desired, r -> context.getClient().resource(r).patch(), options);
  }

  public <R extends HasMetadata> R jsonMergePatch(
      R desired, InformerEventSource<R, P> informerEventSource, Options options) {
    return resourcePatch(
        desired, r -> context.getClient().resource(r).patch(), informerEventSource, options);
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
    return jsonMergePatchStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH_STATUS));
  }

  public <R extends HasMetadata> R jsonMergePatchStatus(R resource, Options options) {
    return resourcePatch(resource, r -> context.getClient().resource(r).patchStatus(), options);
  }

  public <R extends HasMetadata> R jsonMergePatchStatus(
      R resource, InformerEventSource<R, P> informerEventSource, Options options) {
    return resourcePatch(
        resource, r -> context.getClient().resource(r).patchStatus(), informerEventSource, options);
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
    return jsonMergePatchPrimary(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH));
  }

  public P jsonMergePatchPrimary(P resource, Options options) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
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
    return jsonMergePatchPrimaryStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH_STATUS));
  }

  public P jsonMergePatchPrimaryStatus(P resource, Options options) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patchStatus(),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
  }

  public <R extends HasMetadata> R resourcePatch(R resource, UnaryOperator<R> updateOperation) {
    return resourcePatch(resource, updateOperation, Options.filterIfOptimisticLocking());
  }

  /**
   * @param desired resource to patch
   * @param updateOperation operation to perform (update, patch, edit, etc.)
   * @return updated resource
   * @param <R> resource type
   * @throws IllegalStateException if no event source or multiple event sources are found
   */
  @Experimental(API_MIGHT_CHANGE)
  @SuppressWarnings({"rawtypes", "unchecked"})
  private <R extends HasMetadata> R resourcePatch(
      R desired, UnaryOperator<R> updateOperation, Options options) {
    return resourcePatch(desired, null, updateOperation, options);
  }

  @Experimental(API_MIGHT_CHANGE)
  @SuppressWarnings({"rawtypes", "unchecked"})
  private <R extends HasMetadata> R resourcePatch(
      R desired, R actual, UnaryOperator<R> updateOperation, Options options) {

    Class<?> targetClass = desired == null ? actual.getClass() : desired.getClass();

    var esList = context.eventSourceRetriever().getEventSourcesFor(targetClass);
    if (esList.isEmpty()) {
      throw new IllegalStateException("No event source found for type: " + targetClass);
    }
    var es = esList.get(0);
    if (esList.size() > 1) {
      log.warn(
          "Multiple event sources found for type: {}, selecting first with name {}",
          targetClass,
          es.name());
    }
    if (es instanceof ManagedInformerEventSource mes) {
      return resourcePatch(
          desired, actual, updateOperation, (ManagedInformerEventSource<R, P, ?>) mes, options);
    } else {
      throw new IllegalStateException(
          "Target event source must be a subclass off "
              + ManagedInformerEventSource.class.getName());
    }
  }

  public <R extends HasMetadata> R resourcePatch(
      R desired, UnaryOperator<R> updateOperation, ManagedInformerEventSource<R, P, ?> ies) {
    return resourcePatch(desired, updateOperation, Options.filterIfOptimisticLocking());
  }

  public <R extends HasMetadata> R resourcePatch(
      R desired,
      UnaryOperator<R> updateOperation,
      ManagedInformerEventSource<R, P, ?> ies,
      Options options) {

    return resourcePatch(desired, null, updateOperation, ies, options);
  }

  public <R extends HasMetadata> R resourcePatch(
      R desiredResource,
      R actualResource,
      UnaryOperator<R> updateOperation,
      ManagedInformerEventSource<R, P, ?> ies,
      Options options) {

    var matcher = options.getMatcher().orElse(null);
    boolean matches = false;
    if (matcher != null) {
      if (actualResource == null) {
        actualResource = ies.get(ResourceID.fromResource(desiredResource)).orElse(null);
      }
      // todo describe might require optimistic locking
      if (actualResource != null) {
        matches = matcher.matches(desiredResource, actualResource, context);
      }
    }
    if (options.requiresMatcher() && matcher == null) {
      throw new IllegalArgumentException("Mode : " + options.mode + " requires matcher");
    }
    if (matches) {
      return actualResource;
    }
    boolean optimisticLocking = desiredResource.getMetadata().getResourceVersion() != null;

    if (options.getMode() == Mode.CACHE_ONLY
        || (options.getMode() == Mode.FILTER_IF_OPTIMISTIC_LOCKING && !optimisticLocking)) {
      return ies.updateAndCacheResource(desiredResource, updateOperation);
    } else {
      return ies.eventFilteringUpdateAndCacheResource(desiredResource, updateOperation);
    }
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

      return serverSideApplyPrimary(resource, Options.cacheOnly());
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

  @Experimental(API_MIGHT_CHANGE)
  public static class Options {

    private static final Options ALWAYS_FILTER = new Options(Mode.FORCE_FILTER, null);
    private static final Options ONLY_CACHE = new Options(Mode.CACHE_ONLY, null);
    private static final Options FILTER_IF_OPTIMISTIC_LOCKING =
        new Options(Mode.FILTER_IF_OPTIMISTIC_LOCKING, null);

    private final Mode mode;
    private Matcher matcher;

    private Options(Mode mode, Matcher matcher) {
      this.mode = mode;
      this.matcher = matcher;
    }

    public static Options forceFilterEvents() {
      return ALWAYS_FILTER;
    }

    public static Options cacheOnly() {
      return ONLY_CACHE;
    }

    public static Options cacheOnly(Matcher matcher) {
      return new Options(Mode.CACHE_ONLY, matcher);
    }

    public static Options filterIfOptimisticLocking() {
      return FILTER_IF_OPTIMISTIC_LOCKING;
    }

    /**
     * Filters own updates and, before doing so, checks whether the actual resource already matches
     * the desired state using the given {@link Matcher}.
     *
     * @param matcher the matcher used to decide whether actual already matches desired
     */
    public static Options matchAndFilter(Matcher matcher) {
      if (matcher == null) {
        throw new IllegalArgumentException("Matcher cannot be null for matchAndFilterOperation");
      }
      return new Options(Mode.FILTER_IF_NOT_MATCHING, matcher);
    }

    public static Options matchAndFilterWithDefaultMatcher(UpdateType updateType) {
      return new Options(Mode.FILTER_IF_NOT_MATCHING, updateType.getMatcher());
    }

    public Mode getMode() {
      return mode;
    }

    public Optional<Matcher> getMatcher() {
      return Optional.ofNullable(matcher);
    }

    public boolean requiresMatcher() {
      return mode == Mode.FILTER_IF_NOT_MATCHING;
    }
  }

  @Experimental(API_MIGHT_CHANGE)
  public enum Mode {
    FILTER_IF_OPTIMISTIC_LOCKING,
    FILTER_IF_NOT_MATCHING,
    CACHE_ONLY,
    FORCE_FILTER,
  }

  private <T extends HasMetadata> T desiredForJsonPatch(
      T actualResource, UnaryOperator<T> unaryOperator, Options options) {
    if (options.getMatcher().isPresent()) {
      var cloned =
          context
              .getControllerConfiguration()
              .getConfigurationService()
              .getResourceCloner()
              .clone(actualResource);
      return unaryOperator.apply(cloned);
    }
    return null;
  }
}
