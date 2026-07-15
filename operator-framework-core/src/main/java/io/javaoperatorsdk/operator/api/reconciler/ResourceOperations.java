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
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.processing.matcher.UpdateType;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Provides various, useful operations to manipulate resources (server-side apply, patch, etc.) in
 * an idiomatic ways. Provides improved update/patch/create operations to make sure that the latest
 * version of the resource is present in the caches for the next reconciliation, and filter own
 * update events. You can still use kubernetes client directly, these methods are however useful to
 * achieve better efficiency.
 *
 * <p>Every update/patch/create method comes in two flavors: a default one, and one that takes an
 * {@link Options} argument to control how the resulting own event is handled. The behavior is
 * selected through {@link Mode}:
 *
 * <ul>
 *   <li>{@link Options#filterWithOptimisticLocking()} ({@link Mode#FILTER_WITH_OPTIMISTIC_LOCKING})
 *       - the own event is filtered; the write must use optimistic locking (i.e. a resource version
 *       is set on the resource being written), otherwise an {@link IllegalArgumentException} is
 *       thrown. Requiring optimistic locking guarantees that a concurrent third-party change is
 *       rejected by the API server rather than silently filtered out.
 *   <li>{@link Options#matchAndFilter(Matcher)} / {@link
 *       Options#matchAndFilterWithDefaultMatcher(io.javaoperatorsdk.operator.processing.matcher.UpdateType)}
 *       ({@link Mode#FILTER_IF_NOT_MATCHING}) - before writing, the desired state is compared to
 *       the actual (cached) state using the provided {@link Matcher}; if they already match the
 *       write is skipped, otherwise it is performed and the own event is filtered.
 *   <li>{@link Options#cacheOnly()} ({@link Mode#CACHE_ONLY}) - the response is only put into the
 *       cache (read-cache-after-write consistency) and no own-event filtering is done.
 *   <li>{@link Options#forceFilterEvents()} ({@link Mode#FORCE_FILTER}) - the own event is always
 *       filtered, regardless of optimistic locking. Use only when correctness is otherwise
 *       guaranteed (see the note below). This is mostly for internal usage, like in {@link
 *       io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource}
 *       where we match the resource explicitly before update.
 * </ul>
 *
 * <p><strong>Correctness of event filtering.</strong> Filtering an own update event is only safe if
 * the framework can tell an own write apart from a concurrent third-party write. This requires
 * <em>either</em>:
 *
 * <ul>
 *   <li>a {@link Matcher} to be provided (so a filtered event can be confirmed to match the desired
 *       state we just wrote), <em>or</em>
 *   <li>the update to be done using <em>optimistic locking</em> (a resource version set on the
 *       written resource), so a conflicting concurrent change is rejected by the API server rather
 *       than silently swallowed.
 * </ul>
 *
 * <p>If neither holds - for example {@link Options#forceFilterEvents()} on a write without
 * optimistic locking and without a matcher - a concurrent external update happening within the
 * filtering window may be filtered out and thus missed until the next resync. Prefer providing a
 * matcher or using optimistic locking whenever own-event filtering is desired.
 *
 * <p><strong>Default matchers.</strong> For the matching modes a default {@link Matcher} is
 * provided for every {@link io.javaoperatorsdk.operator.processing.matcher.UpdateType} (see {@link
 * Options#matchAndFilterWithDefaultMatcher(io.javaoperatorsdk.operator.processing.matcher.UpdateType)}),
 * so matching works out of the box. Note however that these default matchers are heuristics and may
 * have issues in some edge cases, so a workflow relying on them should be tested against the
 * concrete resources it manages. When a default matcher does not fit, provide your own via {@link
 * Options#matchAndFilter(Matcher)}.
 *
 * <p>Despite that caveat, matching is generally the most efficient way to handle updates: it covers
 * full event filtering <em>and</em> only performs a write when the actual state actually differs
 * from the desired one, thus also reducing the number of requests made against the Kubernetes API
 * server.
 *
 * @param <P> the primary resource type on which this object operates
 */
public class ResourceOperations<P extends HasMetadata> {

  public static final int DEFAULT_MAX_RETRY = 10;

  private static final Logger log = LoggerFactory.getLogger(ResourceOperations.class);

  private final Context<P> context;

  public ResourceOperations(Context<P> context) {
    this.context = context;
  }

  /**
   * Server-Side Applies the resource, then caches the response so the next reconciliation observes
   * it (read-cache-after-write consistency).
   *
   * <p>Uses the {@link UpdateType#SSA} default {@link Matcher}: the apply is skipped when the
   * actual (cached) state already matches the desired one, otherwise it is performed and the
   * resulting own event is filtered so it does not trigger a reconciliation. Use {@link
   * #serverSideApply( HasMetadata, Options)} to change this behavior. SSA does not require
   * optimistic locking.
   *
   * @param resource the desired resource to server-side apply
   * @param <R> the resource type
   * @return the applied resource as returned by the API server
   */
  public <R extends HasMetadata> R serverSideApply(R resource) {
    return serverSideApply(resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA));
  }

  /**
   * Server-Side Applies the resource, controlling caching and own-event handling through the given
   * {@link Options}.
   *
   * @param resource the desired resource to server-side apply
   * @param options controls caching and own-event filtering; see {@link Options} and the class
   *     documentation
   * @param <R> the resource type
   * @return the applied resource as returned by the API server
   */
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

  /**
   * Server-Side Applies the resource, caching and filtering the own event through the given {@link
   * InformerEventSource} (instead of the controller's own event source). Uses the {@link
   * UpdateType#SSA} default {@link Matcher}.
   *
   * @param resource the desired resource to server-side apply
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param <R> the resource type
   * @return the applied resource as returned by the API server
   */
  public <R extends HasMetadata> R serverSideApply(
      R resource, InformerEventSource<R, P> informerEventSource) {
    return serverSideApply(
        resource, informerEventSource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA));
  }

  /**
   * Server-Side Applies the resource, caching and filtering the own event through the given {@link
   * InformerEventSource} and using the given {@link Options}. When {@code informerEventSource} is
   * {@code null} this falls back to {@link #serverSideApply(HasMetadata)}.
   *
   * @param resource the desired resource to server-side apply
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the applied resource as returned by the API server
   */
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
   * Server-Side Applies the {@code status} subresource, controlling caching and own-event handling
   * through the given {@link Options}.
   *
   * @param resource the desired resource (with the status to apply)
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the applied resource as returned by the API server
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

  /**
   * Server-Side Applies the {@code status} subresource, caching the response and filtering the own
   * event. Uses the {@link UpdateType#SSA_STATUS} default {@link Matcher}.
   *
   * @param resource the desired resource (with the status to apply)
   * @param <R> the resource type
   * @return the applied resource as returned by the API server
   */
  public <R extends HasMetadata> R serverSideApplyStatus(R resource) {
    return serverSideApplyStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA_STATUS));
  }

  /**
   * Server-Side Applies the primary resource, caching and filtering the own event through the
   * controller's own event source. Uses the {@link UpdateType#SSA} default {@link Matcher}.
   *
   * @param resource the desired primary resource to server-side apply
   * @return the applied resource as returned by the API server
   */
  public P serverSideApplyPrimary(P resource) {
    return serverSideApplyPrimary(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA));
  }

  /**
   * Server-Side Applies the primary resource, caching and filtering the own event through the
   * controller's own event source and using the given {@link Options}.
   *
   * @param resource the desired primary resource to server-side apply
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the applied resource as returned by the API server
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
   * Server-Side Applies the primary resource {@code status} subresource, caching and filtering the
   * own event through the controller's own event source. Uses the {@link UpdateType#SSA_STATUS}
   * default {@link Matcher}.
   *
   * @param desired the desired primary resource (with the status to apply)
   * @return the applied resource as returned by the API server
   */
  public P serverSideApplyPrimaryStatus(P desired) {
    return serverSideApplyPrimaryStatus(
        desired, Options.matchAndFilterWithDefaultMatcher(UpdateType.SSA_STATUS));
  }

  /**
   * Server-Side Applies the primary resource {@code status} subresource, caching and filtering the
   * own event through the controller's own event source and using the given {@link Options}.
   *
   * @param desired the desired primary resource (with the status to apply)
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the applied resource as returned by the API server
   */
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

  /**
   * Updates (HTTP PUT) the resource, then caches the response so the next reconciliation observes
   * it (read-cache-after-write consistency).
   *
   * <p>Uses the {@link UpdateType#UPDATE} default {@link Matcher}: the update is skipped when the
   * actual (cached) state already matches the desired one, otherwise it is performed using
   * optimistic locking (the resource version must be set, otherwise an {@link
   * IllegalArgumentException} is thrown) and the resulting own event is filtered. Use {@link
   * #update(HasMetadata, Options)} to change this.
   *
   * @param resource the desired resource to update
   * @param <R> the resource type
   * @return the updated resource as returned by the API server
   */
  public <R extends HasMetadata> R update(R resource) {
    return update(resource, Options.filterWithOptimisticLocking(UpdateType.UPDATE));
  }

  /**
   * Updates (HTTP PUT) the resource, controlling caching and own-event handling through the given
   * {@link Options}.
   *
   * @param desired the desired resource to update
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the updated resource as returned by the API server
   */
  public <R extends HasMetadata> R update(R desired, Options options) {
    return resourcePatch(desired, r -> context.getClient().resource(r).update(), options);
  }

  /**
   * Updates (HTTP PUT) the resource, caching and filtering the own event through the given {@link
   * InformerEventSource}. Uses the {@link UpdateType#UPDATE} default {@link Matcher} and optimistic
   * locking: the update is skipped when the actual state already matches, otherwise it is performed
   * with optimistic locking (the resource version must be set) and the own event is filtered.
   *
   * @param resource the desired resource to update
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param <R> the resource type
   * @return the updated resource as returned by the API server
   */
  public <R extends HasMetadata> R update(
      R resource, InformerEventSource<R, P> informerEventSource) {
    return update(
        resource, informerEventSource, Options.filterWithOptimisticLocking(UpdateType.UPDATE));
  }

  /**
   * Updates (HTTP PUT) the resource, caching and filtering the own event through the given {@link
   * InformerEventSource} and using the given {@link Options}. When {@code informerEventSource} is
   * {@code null} this falls back to {@link #update(HasMetadata)}.
   *
   * @param resource the desired resource to update
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the updated resource as returned by the API server
   */
  public <R extends HasMetadata> R update(
      R resource, InformerEventSource<R, P> informerEventSource, Options options) {
    if (informerEventSource == null) {
      return update(resource);
    }
    return resourcePatch(
        resource, r -> context.getClient().resource(r).update(), informerEventSource, options);
  }

  /**
   * Creates the resource, then caches the response so the next reconciliation observes it
   * (read-cache-after-write consistency). The resulting own event is filtered.
   *
   * @param resource the resource to create
   * @param <R> the resource type
   * @return the created resource as returned by the API server
   */
  public <R extends HasMetadata> R create(R resource) {
    // it is safe to do event filtering for create since check if the resource already exists.
    return create(resource, Options.forceFilterEvents());
  }

  /**
   * Creates the resource, controlling caching and own-event handling through the given {@link
   * Options}. A matcher is not supported for create (there is nothing to match against) and passing
   * one throws {@link IllegalArgumentException}.
   *
   * @param resource the resource to create
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the created resource as returned by the API server
   * @throws IllegalArgumentException if the options carry a {@link Matcher}
   */
  public <R extends HasMetadata> R create(R resource, Options options) {
    if (options.getMatcher().isPresent()) {
      throw new IllegalArgumentException(
          "Create operation does not support matcher. There is nothing to match.");
    }
    return resourcePatch(resource, r -> context.getClient().resource(r).create(), options);
  }

  /**
   * Creates the resource, caching and filtering the own event through the given {@link
   * InformerEventSource} and using the given {@link Options}. When {@code informerEventSource} is
   * {@code null} this falls back to {@link #create(HasMetadata)}.
   *
   * @param resource the resource to create
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the created resource as returned by the API server
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
   * Updates (HTTP PUT) the resource {@code status} subresource, caching the response and filtering
   * the own event. Uses the {@link UpdateType#UPDATE_STATUS} default {@link Matcher} and optimistic
   * locking: the update is skipped when the actual status already matches, otherwise it is
   * performed with optimistic locking (the resource version must be set, otherwise an {@link
   * IllegalArgumentException} is thrown) and the own event is filtered.
   *
   * @param resource the desired resource (with the status to update)
   * @param <R> the resource type
   * @return the updated resource as returned by the API server
   */
  public <R extends HasMetadata> R updateStatus(R resource) {
    return updateStatus(resource, Options.filterWithOptimisticLocking(UpdateType.UPDATE_STATUS));
  }

  /**
   * Updates (HTTP PUT) the resource {@code status} subresource, controlling caching and own-event
   * handling through the given {@link Options}.
   *
   * @param resource the desired resource (with the status to update)
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the updated resource as returned by the API server
   */
  public <R extends HasMetadata> R updateStatus(R resource, Options options) {
    return resourcePatch(resource, r -> context.getClient().resource(r).updateStatus(), options);
  }

  /**
   * Updates (HTTP PUT) the primary resource, caching and filtering the own event through the
   * controller's own event source. Uses the {@link UpdateType#UPDATE} default {@link Matcher} and
   * optimistic locking: the update is skipped when the actual state already matches, otherwise it
   * is performed with optimistic locking (the resource version must be set) and the own event is
   * filtered.
   *
   * @param desired the desired primary resource to update
   * @return the updated resource as returned by the API server
   */
  public P updatePrimary(P desired) {
    return updatePrimary(desired, Options.filterWithOptimisticLocking(UpdateType.UPDATE));
  }

  /**
   * Updates (HTTP PUT) the primary resource, caching and filtering the own event through the
   * controller's own event source and using the given {@link Options}.
   *
   * @param desired the desired primary resource to update
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the updated resource as returned by the API server
   */
  public P updatePrimary(P desired, Options options) {
    return resourcePatch(
        desired,
        r -> context.getClient().resource(r).update(),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
  }

  /**
   * Updates (HTTP PUT) the primary resource {@code status} subresource, caching and filtering the
   * own event through the controller's own event source. Uses the {@link UpdateType#UPDATE_STATUS}
   * default {@link Matcher} and optimistic locking: the update is skipped when the actual status
   * already matches, otherwise it is performed with optimistic locking (the resource version must
   * be set) and the own event is filtered.
   *
   * @param desired the desired primary resource (with the status to update)
   * @return the updated resource as returned by the API server
   */
  public P updatePrimaryStatus(P desired) {
    return resourcePatch(
        desired,
        r -> context.getClient().resource(r).updateStatus(),
        context.eventSourceRetriever().getControllerEventSource(),
        Options.filterWithOptimisticLocking(UpdateType.UPDATE_STATUS));
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the resource: the {@code unaryOperator} mutates the resource
   * and the diff is sent as a patch. Caches the response and filters the own event using the {@link
   * UpdateType#JSON_PATCH} default {@link Matcher}. Use {@link #jsonPatch(HasMetadata,
   * UnaryOperator, Options)} to change this behavior.
   *
   * @param actualResource the current resource used as the base of the patch
   * @param unaryOperator function that mutates the resource into its desired state
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonPatch(R actualResource, UnaryOperator<R> unaryOperator) {
    return jsonPatch(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH));
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the resource, controlling caching and own-event handling
   * through the given {@link Options}.
   *
   * @param actualResource the current resource used as the base of the patch
   * @param unaryOperator function that mutates the resource into its desired state
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonPatch(
      R actualResource, UnaryOperator<R> unaryOperator, Options options) {
    R desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).edit(rr -> desired),
        options);
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the resource, caching and filtering the own event through
   * the given {@link InformerEventSource} and using the given {@link Options}.
   *
   * @param actualResource the current resource used as the base of the patch
   * @param unaryOperator function that mutates the resource into its desired state
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonPatch(
      R actualResource,
      UnaryOperator<R> unaryOperator,
      InformerEventSource<R, P> informerEventSource,
      Options options) {
    R desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).edit(rr -> desired),
        informerEventSource,
        options);
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the resource {@code status} subresource: the {@code
   * unaryOperator} mutates the resource status and the diff is sent as a patch. Caches the response
   * and filters the own event using the {@link UpdateType#JSON_PATCH_STATUS} default {@link
   * Matcher}.
   *
   * @param actualResource the current resource used as the base of the patch
   * @param unaryOperator function that mutates the resource status into its desired state
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonPatchStatus(
      R actualResource, UnaryOperator<R> unaryOperator) {
    return jsonPatchStatus(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH_STATUS));
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the resource {@code status} subresource, controlling caching
   * and own-event handling through the given {@link Options}.
   *
   * @param actualResource the current resource used as the base of the patch
   * @param unaryOperator function that mutates the resource status into its desired state
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonPatchStatus(
      R actualResource, UnaryOperator<R> unaryOperator, Options options) {
    R desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).editStatus(rr -> desired),
        options);
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the resource {@code status} subresource, caching and
   * filtering the own event through the given {@link InformerEventSource} and using the given
   * {@link Options}.
   *
   * @param actualResource the current resource used as the base of the patch
   * @param unaryOperator function that mutates the resource status into its desired state
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonPatchStatus(
      R actualResource,
      UnaryOperator<R> unaryOperator,
      InformerEventSource<R, P> informerEventSource,
      Options options) {
    R desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).editStatus(rr -> desired),
        informerEventSource,
        options);
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the primary resource, caching and filtering the own event
   * through the controller's own event source. Uses the {@link UpdateType#JSON_PATCH} default
   * {@link Matcher}.
   *
   * @param actualResource the current primary resource used as the base of the patch
   * @param unaryOperator function that mutates the resource into its desired state
   * @return the patched resource as returned by the API server
   */
  public P jsonPatchPrimary(P actualResource, UnaryOperator<P> unaryOperator) {
    return jsonPatchPrimary(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH));
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the primary resource, caching and filtering the own event
   * through the controller's own event source and using the given {@link Options}.
   *
   * @param actualResource the current primary resource used as the base of the patch
   * @param unaryOperator function that mutates the resource into its desired state
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the patched resource as returned by the API server
   */
  public P jsonPatchPrimary(P actualResource, UnaryOperator<P> unaryOperator, Options options) {
    P desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).edit(rr -> desired),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the primary resource {@code status} subresource, caching and
   * filtering the own event through the controller's own event source. Uses the {@link
   * UpdateType#JSON_PATCH_STATUS} default {@link Matcher}.
   *
   * @param actualResource the current primary resource used as the base of the patch
   * @param unaryOperator function that mutates the resource status into its desired state
   * @return the patched resource as returned by the API server
   */
  public P jsonPatchPrimaryStatus(P actualResource, UnaryOperator<P> unaryOperator) {
    return jsonPatchPrimaryStatus(
        actualResource,
        unaryOperator,
        Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH_STATUS));
  }

  /**
   * Applies a JSON Patch (RFC 6902) to the primary resource {@code status} subresource, caching and
   * filtering the own event through the controller's own event source and using the given {@link
   * Options}.
   *
   * @param actualResource the current primary resource used as the base of the patch
   * @param unaryOperator function that mutates the resource status into its desired state
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the patched resource as returned by the API server
   */
  public P jsonPatchPrimaryStatus(
      P actualResource, UnaryOperator<P> unaryOperator, Options options) {
    P desired = desiredForJsonPatch(actualResource, unaryOperator, options);
    return resourcePatch(
        desired,
        actualResource,
        r -> context.getClient().resource(actualResource).status().edit(rr -> desired),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the resource, merging the provided resource with the
   * one on the server. Caches the response and filters the own event using the {@link
   * UpdateType#JSON_MERGE_PATCH} default {@link Matcher}. Use {@link #jsonMergePatch(HasMetadata,
   * Options)} to change this behavior.
   *
   * @param desired the desired resource to merge-patch
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonMergePatch(R desired) {
    return jsonMergePatch(
        desired, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH));
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the resource, controlling caching and own-event
   * handling through the given {@link Options}.
   *
   * @param desired the desired resource to merge-patch
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonMergePatch(R desired, Options options) {
    return resourcePatch(desired, r -> context.getClient().resource(r).patch(), options);
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the resource, caching and filtering the own event
   * through the given {@link InformerEventSource} and using the given {@link Options}.
   *
   * @param desired the desired resource to merge-patch
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonMergePatch(
      R desired, InformerEventSource<R, P> informerEventSource, Options options) {
    return resourcePatch(
        desired, r -> context.getClient().resource(r).patch(), informerEventSource, options);
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the resource {@code status} subresource, caching the
   * response and filtering the own event. Uses the {@link UpdateType#JSON_MERGE_PATCH_STATUS}
   * default {@link Matcher}.
   *
   * @param resource the desired resource (with the status to merge-patch)
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonMergePatchStatus(R resource) {
    return jsonMergePatchStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH_STATUS));
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the resource {@code status} subresource, controlling
   * caching and own-event handling through the given {@link Options}.
   *
   * @param resource the desired resource (with the status to merge-patch)
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonMergePatchStatus(R resource, Options options) {
    return resourcePatch(resource, r -> context.getClient().resource(r).patchStatus(), options);
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the resource {@code status} subresource, caching and
   * filtering the own event through the given {@link InformerEventSource} and using the given
   * {@link Options}.
   *
   * @param resource the desired resource (with the status to merge-patch)
   * @param informerEventSource the event source used for caching and own-event filtering
   * @param options controls caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the patched resource as returned by the API server
   */
  public <R extends HasMetadata> R jsonMergePatchStatus(
      R resource, InformerEventSource<R, P> informerEventSource, Options options) {
    return resourcePatch(
        resource, r -> context.getClient().resource(r).patchStatus(), informerEventSource, options);
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the primary resource, caching and filtering the own
   * event through the controller's own event source. Uses the {@link UpdateType#JSON_MERGE_PATCH}
   * default {@link Matcher}.
   *
   * @param resource the desired primary resource to merge-patch
   * @return the patched resource as returned by the API server
   */
  public P jsonMergePatchPrimary(P resource) {
    return jsonMergePatchPrimary(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH));
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the primary resource, caching and filtering the own
   * event through the controller's own event source and using the given {@link Options}.
   *
   * @param resource the desired primary resource to merge-patch
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the patched resource as returned by the API server
   */
  public P jsonMergePatchPrimary(P resource, Options options) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the primary resource {@code status} subresource,
   * caching and filtering the own event through the controller's own event source. Uses the {@link
   * UpdateType#JSON_MERGE_PATCH_STATUS} default {@link Matcher}.
   *
   * @param resource the desired primary resource (with the status to merge-patch)
   * @return the patched resource as returned by the API server
   */
  public P jsonMergePatchPrimaryStatus(P resource) {
    return jsonMergePatchPrimaryStatus(
        resource, Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_MERGE_PATCH_STATUS));
  }

  /**
   * Applies a JSON Merge Patch (RFC 7386) to the primary resource {@code status} subresource,
   * caching and filtering the own event through the controller's own event source and using the
   * given {@link Options}.
   *
   * @param resource the desired primary resource (with the status to merge-patch)
   * @param options controls caching and own-event filtering; see {@link Options}
   * @return the patched resource as returned by the API server
   */
  public P jsonMergePatchPrimaryStatus(P resource, Options options) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patchStatus(),
        context.eventSourceRetriever().getControllerEventSource(),
        options);
  }

  /**
   * Low-level building block behind all the update/patch operations above: runs the given {@code
   * updateOperation} against the API server, then caches the response and (depending on the {@link
   * Options}) filters the resulting own event. The target event source is resolved automatically
   * from the desired resource's type. Uses {@link Options#filterWithOptimisticLocking()}.
   *
   * @param resource the desired resource being written
   * @param updateOperation the actual write to perform (update, patch, edit, ...) returning the
   *     server response
   * @param <R> the resource type
   * @return the resource as returned by {@code updateOperation}
   * @throws IllegalStateException if no (or no matching) event source is found for the resource
   *     type
   */
  public <R extends HasMetadata> R resourcePatch(R resource, UnaryOperator<R> updateOperation) {
    return resourcePatch(resource, updateOperation, Options.filterWithOptimisticLocking());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private <R extends HasMetadata> R resourcePatch(
      R desired, UnaryOperator<R> updateOperation, Options options) {
    return resourcePatch(desired, null, updateOperation, options);
  }

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

  /**
   * This method is public to ensure backward compatibility, we will make it private in next major
   * release.
   *
   * @deprecated use one of the higher-level update/patch methods, or {@link #resourcePatch(
   *     HasMetadata, HasMetadata, UnaryOperator, ManagedInformerEventSource, Options)}
   */
  @Deprecated(forRemoval = true)
  public <R extends HasMetadata> R resourcePatch(
      R desired, UnaryOperator<R> updateOperation, ManagedInformerEventSource<R, P, ?> ies) {
    return resourcePatch(desired, updateOperation, ies, Options.filterWithOptimisticLocking());
  }

  private <R extends HasMetadata> R resourcePatch(
      R desired,
      UnaryOperator<R> updateOperation,
      ManagedInformerEventSource<R, P, ?> ies,
      Options options) {

    return resourcePatch(desired, null, updateOperation, ies, options);
  }

  /**
   * The core update/patch routine used by all operations. Optionally matches the desired against
   * the actual (cached) state, performs the write through {@code updateOperation}, and caches the
   * response while filtering the own event according to the {@link Options}/{@link Mode}.
   *
   * <p>When a {@link Matcher} is present but {@code actualResource} is {@code null}, the actual
   * state is looked up from the given event source's cache. If the states already match, the write
   * is skipped and the actual resource is returned unchanged.
   *
   * @param desiredResource the desired resource; may be {@code null} for JSON Patch operations,
   *     where {@code actualResource} is used as the base instead
   * @param actualResource the actual (current) resource used for matching; may be {@code null}
   * @param updateOperation the actual write to perform, returning the server response
   * @param ies the event source used for caching and own-event filtering
   * @param options controls matching, caching and own-event filtering; see {@link Options}
   * @param <R> the resource type
   * @return the resource as returned by {@code updateOperation}, or {@code actualResource} when the
   *     desired state already matches
   * @throws IllegalArgumentException if the mode requires a matcher but none is provided, or if the
   *     mode is {@link Mode#FILTER_WITH_OPTIMISTIC_LOCKING} but the resource being written has no
   *     resource version set
   */
  // visible for testing
  <R extends HasMetadata> R resourcePatch(
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
      if (actualResource != null) {
        matches = matcher.matches(desiredResource, actualResource, context);
      }
    }
    if (options.requiresMatcher() && matcher == null) {
      throw new IllegalArgumentException("Mode : " + options.mode + " requires matcher");
    }
    // this is to cover special case for jsonPatch were we should use actual resource as base
    if (matches) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Resource match resource id: {}, type: {}, version: {}",
            ResourceID.fromResource(desiredResource),
            desiredResource.getClass().getSimpleName(),
            desiredResource.getMetadata().getResourceVersion());
      }
      return actualResource;
    }

    if (options.getMode() == Mode.FILTER_WITH_OPTIMISTIC_LOCKING
        && desiredResource.getMetadata().getResourceVersion() == null) {
      throw new IllegalArgumentException(
          "Mode "
              + Mode.FILTER_WITH_OPTIMISTIC_LOCKING
              + " requires optimistic locking, but no resource version is set on the resource: "
              + ResourceID.fromResource(desiredResource));
    }

    if (options.getMode() == Mode.CACHE_ONLY) {
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
    return addFinalizer(context.getControllerConfiguration().getFinalizerName(), false);
  }

  /**
   * Adds the default finalizer (from controller configuration) to the primary resource. This is a
   * convenience method that calls {@link #addFinalizer(String, boolean)} with the configured
   * finalizer name.
   *
   * @param cacheOnly if {@code true} the finalizer patch does not filter its own event (see {@link
   *     #addFinalizer(String, boolean)})
   * @return updated resource from the server response
   * @see #addFinalizer(String, boolean)
   */
  public P addFinalizer(boolean cacheOnly) {
    return addFinalizer(context.getControllerConfiguration().getFinalizerName(), cacheOnly);
  }

  /**
   * Adds the given finalizer to the primary resource, filtering the own event (equivalent to {@link
   * #addFinalizer(String, boolean)} with {@code cacheOnly = false}).
   *
   * @param finalizerName name of the finalizer to add
   * @return updated resource from the server response
   * @see #addFinalizer(String, boolean)
   */
  public P addFinalizer(String finalizerName) {
    return addFinalizer(finalizerName, false);
  }

  /**
   * Adds finalizer to the resource using JSON Patch. Retries conflicts and unprocessable content
   * (HTTP 422). It does not try to add finalizer if there is already a finalizer or resource is
   * marked for deletion. Note that explicitly adding/removing finalizer is required only if
   * "Trigger reconciliation on all event" mode is on.
   *
   * @param finalizerName name of the finalizer to add
   * @param cacheOnly if {@code true} the finalizer patch only caches the response and does
   *     <em>not</em> filter the resulting own event, so a subsequent reconciliation is triggered by
   *     the finalizer addition; if {@code false} the default JSON Patch matcher is used and the own
   *     event is filtered
   * @return updated resource from the server response
   */
  public P addFinalizer(String finalizerName, boolean cacheOnly) {
    var resource = context.getPrimaryResource();
    if (resource.isMarkedForDeletion() || resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatchPrimary(
        r -> {
          r.addFinalizer(finalizerName);
          return r;
        },
        r -> !r.hasFinalizer(finalizerName),
        cacheOnly);
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
   * @param finalizerName name of the finalizer to remove
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
   * Patches the primary resource using JSON Patch, retrying on conflict, filtering the own event
   * via the default JSON Patch matcher (equivalent to {@link
   * #conflictRetryingPatchPrimary(UnaryOperator, Predicate, boolean)} with {@code cacheOnly =
   * false}).
   *
   * @param resourceChangesOperator changes to be done on the resource before update
   * @param preCondition condition to check if the patch operation still needs to be performed or
   *     not
   * @return updated resource from the server or unchanged if the precondition does not hold
   */
  public P conflictRetryingPatchPrimary(
      UnaryOperator<P> resourceChangesOperator, Predicate<P> preCondition) {
    return conflictRetryingPatchPrimary(resourceChangesOperator, preCondition, false);
  }

  /**
   * Patches the primary resource using JSON Patch, retrying on conflict. The {@code
   * resourceChangesOperator} is applied to the current resource before each attempt; if the server
   * responds with conflict (HTTP 409) or unprocessable content (HTTP 422) the latest version is
   * re-fetched and the operation is retried, up to {@link ResourceOperations#DEFAULT_MAX_RETRY}
   * times.
   *
   * @param resourceChangesOperator changes to be done on the resource before update
   * @param preCondition condition to check if the patch operation still needs to be performed or
   *     not; evaluated against the (possibly re-fetched) resource before each attempt
   * @param cacheOnly if {@code true} the patch only caches the response and does <em>not</em>
   *     filter the resulting own event; if {@code false} the default JSON Patch matcher is used and
   *     the own event is filtered
   * @return updated resource from the server or unchanged if the precondition does not hold
   * @throws OperatorException if the maximum number of retry attempts is exceeded
   */
  @SuppressWarnings("unchecked")
  public P conflictRetryingPatchPrimary(
      UnaryOperator<P> resourceChangesOperator, Predicate<P> preCondition, boolean cacheOnly) {
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
        return jsonPatchPrimary(
            resource,
            resourceChangesOperator,
            cacheOnly
                ? Options.cacheOnly()
                : Options.matchAndFilterWithDefaultMatcher(UpdateType.JSON_PATCH));
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

  /**
   * Controls how an update/patch/create operation of {@link ResourceOperations} handles the own
   * event resulting from the write. This is the entry point users interact with to tune caching and
   * event filtering; instances are created through the static factory methods rather than a
   * constructor, and each maps to a {@link Mode}.
   *
   * <p>See the {@link ResourceOperations} class documentation for a full description of the
   * available strategies, their correctness requirements (a {@link Matcher} or optimistic locking
   * is needed for safe own-event filtering), and the trade-offs of the default matchers.
   *
   * <ul>
   *   <li>{@link #filterWithOptimisticLocking()} - filter the own event; requires the write to use
   *       optimistic locking (a resource version set), throwing if none is set.
   *   <li>{@link #matchAndFilter(Matcher)} / {@link #matchAndFilterWithDefaultMatcher(UpdateType)}
   *       - skip the write when the desired state already matches the actual state, otherwise write
   *       and filter the own event.
   *   <li>{@link #cacheOnly()} / {@link #cacheOnly(Matcher)} - only cache the response, no
   *       own-event filtering.
   *   <li>{@link #forceFilterEvents()} - always filter the own event (mostly for internal usage).
   * </ul>
   */
  @Experimental(API_MIGHT_CHANGE)
  public static class Options {

    private static final Options ALWAYS_FILTER = new Options(Mode.FORCE_FILTER, null);
    private static final Options ONLY_CACHE = new Options(Mode.CACHE_ONLY, null);
    private static final Options FILTER_WITH_OPTIMISTIC_LOCKING =
        new Options(Mode.FILTER_WITH_OPTIMISTIC_LOCKING, null);

    private final Mode mode;
    private final Matcher matcher;

    private Options(Mode mode, Matcher matcher) {
      this.mode = mode;
      this.matcher = matcher;
    }

    /**
     * Always filters the own event resulting from the write, regardless of optimistic locking. Safe
     * only when correctness is otherwise guaranteed (see {@link ResourceOperations}); mostly for
     * internal usage.
     *
     * @return options that always filter the own event
     */
    public static Options forceFilterEvents() {
      return ALWAYS_FILTER;
    }

    /**
     * Only caches the response of the write for read-cache-after-write consistency, without doing
     * any own-event filtering.
     *
     * @return options that only cache the response
     */
    public static Options cacheOnly() {
      return ONLY_CACHE;
    }

    /**
     * Like {@link #cacheOnly()} but additionally skips the write when the desired state already
     * matches the actual (cached) state according to the given {@link Matcher}.
     *
     * @param matcher the matcher used to decide whether the actual state already matches the
     *     desired
     * @return options that cache only, skipping the write when already matching
     */
    public static Options cacheOnly(Matcher matcher) {
      return new Options(Mode.CACHE_ONLY, matcher);
    }

    /**
     * Filters the own event resulting from the write, requiring the write to use optimistic locking
     * (a resource version set on the written resource). If no resource version is set an {@link
     * IllegalArgumentException} is thrown when the operation is performed. Requiring optimistic
     * locking guarantees that a concurrent third-party change is rejected by the API server rather
     * than being silently filtered out.
     *
     * @return options that filter the own event, requiring optimistic locking
     */
    public static Options filterWithOptimisticLocking() {
      return FILTER_WITH_OPTIMISTIC_LOCKING;
    }

    /**
     * Like {@link #filterWithOptimisticLocking()} but additionally skips the write when the desired
     * state already matches the actual (cached) state according to the given {@link Matcher}. When
     * a write is needed it still requires optimistic locking (see {@link
     * #filterWithOptimisticLocking()}).
     *
     * @param matcher the matcher used to decide whether the actual state already matches the
     *     desired
     * @return options that match using the given matcher and, when a write is needed, filter the
     *     own event requiring optimistic locking
     */
    public static Options filterWithOptimisticLocking(Matcher matcher) {
      return new Options(Mode.FILTER_WITH_OPTIMISTIC_LOCKING, matcher);
    }

    /**
     * Like {@link #filterWithOptimisticLocking()} but additionally skips the write when the desired
     * state already matches the actual (cached) state according to the default {@link Matcher} of
     * the given {@link UpdateType}. When a write is needed it still requires optimistic locking
     * (see {@link #filterWithOptimisticLocking()}).
     *
     * @param updateType the update type whose default matcher should be used
     * @return options that match using the update type's default matcher and, when a write is
     *     needed, filter the own event requiring optimistic locking
     */
    public static Options filterWithOptimisticLocking(UpdateType updateType) {
      return new Options(Mode.FILTER_WITH_OPTIMISTIC_LOCKING, updateType.getMatcher());
    }

    /**
     * Filters own updates and, before doing so, checks whether the actual resource already matches
     * the desired state using the given {@link Matcher}.
     *
     * @param matcher the matcher used to decide whether actual already matches desired
     * @return options that match using the given matcher and filter the own event
     */
    public static Options matchAndFilter(Matcher matcher) {
      if (matcher == null) {
        throw new IllegalArgumentException("Matcher cannot be null for matchAndFilterOperation");
      }
      return new Options(Mode.FILTER_IF_NOT_MATCHING, matcher);
    }

    /**
     * Same as {@link #matchAndFilter(Matcher)} but uses the default {@link Matcher} registered for
     * the given {@link UpdateType}. See the {@link ResourceOperations} class documentation for the
     * caveats of the default matchers.
     *
     * @param updateType the update type whose default matcher should be used
     * @return options that match using the default matcher and filter the own event
     */
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

  /**
   * The strategy used to decide how the own event resulting from a write is handled. This is a
   * low-level enum; users should not reference it directly but select the desired behavior through
   * the {@link Options} factory methods (e.g. {@link Options#filterWithOptimisticLocking()}, {@link
   * Options#matchAndFilter(Matcher)}, {@link Options#cacheOnly()}, {@link
   * Options#forceFilterEvents()}), which is why each constant links to its corresponding factory.
   */
  @Experimental(API_MIGHT_CHANGE)
  public enum Mode {
    /** See {@link Options#filterWithOptimisticLocking()}. */
    FILTER_WITH_OPTIMISTIC_LOCKING,
    /** See {@link Options#matchAndFilter(Matcher)}. */
    FILTER_IF_NOT_MATCHING,
    /** See {@link Options#cacheOnly()}. */
    CACHE_ONLY,
    /** See {@link Options#forceFilterEvents()}. */
    FORCE_FILTER,
  }

  private <T extends HasMetadata> T desiredForJsonPatch(
      T actualResource, UnaryOperator<T> unaryOperator, Options options) {
    var cloned =
        context
            .getControllerConfiguration()
            .getConfigurationService()
            .getResourceCloner()
            .clone(actualResource);
    return unaryOperator.apply(cloned);
  }
}
