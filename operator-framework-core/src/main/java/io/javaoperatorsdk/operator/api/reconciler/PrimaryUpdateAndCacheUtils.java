package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.support.PrimaryResourceCache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Utility methods to patch the primary resource state and store it to the related cache, to make
 * sure that fresh resource is present for the next reconciliation. The main use case for such
 * updates is to store state is resource status. We aim here for completeness and provide you all
 * various options, where all of them have pros and cons.
 *
 * <ul>
 *   <li>(Preferred) Retryable updates with optimistic locking (*withLock) - you can use this
 *       approach out of the box, it updates the resource using optimistic locking and caches the
 *       resource. If the update fails it reads the primary resource and applies the modifications
 *       again and retries the update. After successful update it caches the resource for next
 *       reconciliation. The disadvantage of this method is that theoretically it could fail the max
 *       attempt retry. Note that optimistic locking is essential to have the caching work in
 *       general.
 *   <li>Caching without optimistic locking but with parsing the resource version - to use this you
 *       have to set {@link ConfigurationService#parseResourceVersionsForEventFilteringAndCaching()}
 *       to true. The update won't fail on optimistic locking so there is much higher chance to
 *       succeed. However this bends the rules of Kubernetes API contract by parsing the resource
 *       version. Using this for this purpose is actually a gray area, it should be fine in most of
 *       the setups.
 *   <li>Using {@link PrimaryResourceCache} - in this way you can explicitly ensure freshness or the
 *       resource (see related docs). You don't have to use optimistic locking or parse the resource
 *       version. But requires code from your side and for now (might in future) is not supported in
 *       managed dependent resources.
 * </ul>
 */
public class PrimaryUpdateAndCacheUtils {

  public static final int DEFAULT_MAX_RETRY = 10;

  private PrimaryUpdateAndCacheUtils() {}

  private static final Logger log = LoggerFactory.getLogger(PrimaryUpdateAndCacheUtils.class);

  /**
   * Updates the status with optimistic locking and caches the result for next reconciliation. For
   * details see {@link #updateAndCacheResourceWithLock}.
   */
  public static <P extends HasMetadata> P updateStatusAndCacheResourceWithLock(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResourceWithLock(
        primary,
        context,
        modificationFunction,
        r -> context.getClient().resource(r).updateStatus());
  }

  /**
   * Patches the status using JSON Merge Patch with optimistic locking and caches the result for
   * next reconciliation. For details see {@link #updateAndCacheResourceWithLock}.
   */
  public static <P extends HasMetadata> P patchStatusAndCacheResourceWithLock(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResourceWithLock(
        primary, context, modificationFunction, r -> context.getClient().resource(r).patchStatus());
  }

  /**
   * Patches the status using JSON Patch with optimistic locking and caches the result for next
   * reconciliation. For details see {@link #updateAndCacheResourceWithLock}.
   */
  public static <P extends HasMetadata> P editStatusAndCacheResourceWithLock(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResourceWithLock(
        primary,
        context,
        UnaryOperator.identity(),
        r -> context.getClient().resource(r).editStatus(modificationFunction));
  }

  /**
   * Patches the status using Server Side Apply with optimistic locking and caches the result for
   * next reconciliation. For details see {@link #updateAndCacheResourceWithLock}.
   */
  public static <P extends HasMetadata> P ssaPatchStatusAndCacheResourceWithLock(
      P primary, P freshResourceWithStatus, Context<P> context) {
    return updateAndCacheResourceWithLock(
        primary,
        context,
        r -> freshResourceWithStatus,
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
   * Updates status and makes sure that the up-to-date primary resource will be present during the
   * next reconciliation. Using update (PUT) method.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P updateStatusAndCacheResource(
      P primary, Context<P> context) {
    checkResourceVersionNotPresentAndParseConfiguration(primary, context);
    return patchStatusAndCacheResource(
        primary, context, () -> context.getClient().resource(primary).updateStatus());
  }

  /**
   * Patches status with and makes sure that the up-to-date primary resource will be present during
   * the next reconciliation. Using JSON Merge patch.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchStatusAndCacheResource(
      P primary, Context<P> context) {
    checkResourceVersionNotPresentAndParseConfiguration(primary, context);
    return patchStatusAndCacheResource(
        primary, context, () -> context.getClient().resource(primary).patchStatus());
  }

  /**
   * Patches status and makes sure that the up-to-date primary resource will be present during the
   * next reconciliation. Using JSON Patch.
   *
   * <p>Note that since optimistic locking is not used, there is a risk that JSON Patch will have
   * concurrency issues when removing an element from a list. Since, the list element in JSON Patch
   * are addressed by index, so if a concurrent request removes an element with lower index, the
   * request might be not valid anymore (HTTP 422) or might remove an unmeant element.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P editStatusAndCacheResource(
      P primary, Context<P> context, UnaryOperator<P> operation) {
    checkResourceVersionNotPresentAndParseConfiguration(primary, context);
    return patchStatusAndCacheResource(
        primary, context, () -> context.getClient().resource(primary).editStatus(operation));
  }

  /**
   * Patches the resource with supplied method and makes sure that the up-to-date primary resource
   * will be present during the next reconciliation.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param patch free implementation of cache
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchStatusAndCacheResource(
      P primary, Context<P> context, Supplier<P> patch) {
    var updatedResource = patch.get();
    context
        .eventSourceRetriever()
        .getControllerEventSource()
        .handleRecentResourceUpdate(ResourceID.fromResource(primary), updatedResource, primary);
    return updatedResource;
  }

  /**
   * Patches status and makes sure that the up-to-date primary resource will be present during the
   * next reconciliation. Using Server Side Apply.
   *
   * @param primary resource
   * @param freshResourceWithStatus - fresh resource with target state
   * @param context of reconciliation
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P ssaPatchStatusAndCacheResource(
      P primary, P freshResourceWithStatus, Context<P> context) {
    checkResourceVersionNotPresentAndParseConfiguration(freshResourceWithStatus, context);
    return patchStatusAndCacheResource(
        primary,
        context,
        () ->
            context
                .getClient()
                .resource(freshResourceWithStatus)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()));
  }

  /**
   * Patches the resource status and caches the response in provided {@link PrimaryResourceCache}.
   * Uses Server Side Apply.
   *
   * @param primary resource
   * @param freshResourceWithStatus - fresh resource with target state
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P ssaPatchStatusAndCacheResource(
      P primary, P freshResourceWithStatus, Context<P> context, PrimaryResourceCache<P> cache) {
    checkResourceVersionIsNotPresent(freshResourceWithStatus);
    return patchStatusAndCacheResource(
        primary,
        cache,
        () ->
            context
                .getClient()
                .resource(freshResourceWithStatus)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()));
  }

  /**
   * Patches the resource with JSON Patch and caches the response in provided {@link
   * PrimaryResourceCache}.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P editStatusAndCacheResource(
      P primary, Context<P> context, PrimaryResourceCache<P> cache, UnaryOperator<P> operation) {
    checkResourceVersionIsNotPresent(primary);
    return patchStatusAndCacheResource(
        primary, cache, () -> context.getClient().resource(primary).editStatus(operation));
  }

  /**
   * Patches the resource status with JSON Merge patch and caches the response in provided {@link
   * PrimaryResourceCache}
   *
   * @param primary resource
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchStatusAndCacheResource(
      P primary, Context<P> context, PrimaryResourceCache<P> cache) {
    checkResourceVersionIsNotPresent(primary);
    return patchStatusAndCacheResource(
        primary, cache, () -> context.getClient().resource(primary).patchStatus());
  }

  /**
   * Updates the resource status and caches the response in provided {@link PrimaryResourceCache}.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P updateStatusAndCacheResource(
      P primary, Context<P> context, PrimaryResourceCache<P> cache) {
    checkResourceVersionIsNotPresent(primary);
    return patchStatusAndCacheResource(
        primary, cache, () -> context.getClient().resource(primary).updateStatus());
  }

  /**
   * Updates the resource using the user provided implementation and caches the response in provided
   * {@link PrimaryResourceCache}.
   *
   * @param primary resource
   * @param cache resource cache managed by user
   * @param patch implementation of resource update*
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchStatusAndCacheResource(
      P primary, PrimaryResourceCache<P> cache, Supplier<P> patch) {
    var updatedResource = patch.get();
    cache.cacheResource(primary, updatedResource);
    return updatedResource;
  }

  private static <P extends HasMetadata> void checkResourceVersionIsNotPresent(P primary) {
    if (primary.getMetadata().getResourceVersion() != null) {
      throw new IllegalArgumentException("Resource version is present");
    }
  }

  private static <P extends HasMetadata> void checkResourceVersionNotPresentAndParseConfiguration(
      P primary, Context<P> context) {
    checkResourceVersionIsNotPresent(primary);
    if (!context
        .getControllerConfiguration()
        .getConfigurationService()
        .parseResourceVersionsForEventFilteringAndCaching()) {
      throw new OperatorException(
          "For internal primary resource caching 'parseResourceVersionsForEventFilteringAndCaching'"
              + " must be allowed.");
    }
  }

  /**
   * Modifies the primary using modificationFunction, then uses the modified resource for the
   * request to update with provided update method. But before the update operation sets the
   * resourceVersion to the modified resource from the primary resource, so there is always
   * optimistic locking happening. If the request fails on optimistic update, we read the resource
   * again from the K8S API server and retry the whole process. In short, we make sure we always
   * update the resource with optimistic locking, after we cache the resource in internal cache.
   * Without further going into the details, the optimistic locking is needed so we can reliably
   * handle the caching.
   *
   * @param primary original resource to update
   * @param context of reconciliation
   * @param modificationFunction modifications to make on primary
   * @param updateMethod the update method implementation
   * @return updated resource
   * @param <P> primary type
   */
  public static <P extends HasMetadata> P updateAndCacheResourceWithLock(
      P primary,
      Context<P> context,
      UnaryOperator<P> modificationFunction,
      UnaryOperator<P> updateMethod) {
    return updateAndCacheResourceWithLock(
        primary, context, modificationFunction, updateMethod, DEFAULT_MAX_RETRY);
  }

  /**
   * Modifies the primary using modificationFunction, then uses the modified resource for the
   * request to update with provided update method. But before the update operation sets the
   * resourceVersion to the modified resource from the primary resource, so there is always
   * optimistic locking happening. If the request fails on optimistic update, we read the resource
   * again from the K8S API server and retry the whole process. In short, we make sure we always
   * update the resource with optimistic locking, after we cache the resource in internal cache.
   * Without further going into the details, the optimistic locking is needed so we can reliably
   * handle the caching.
   *
   * @param primary original resource to update
   * @param context of reconciliation
   * @param modificationFunction modifications to make on primary
   * @param updateMethod the update method implementation
   * @param maxRetry - maximum number of retries of conflicts
   * @return updated resource
   * @param <P> primary type
   */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P updateAndCacheResourceWithLock(
      P primary,
      Context<P> context,
      UnaryOperator<P> modificationFunction,
      UnaryOperator<P> updateMethod,
      int maxRetry) {

    if (log.isDebugEnabled()) {
      log.debug("Conflict retrying update for: {}", ResourceID.fromResource(primary));
    }
    P modified = null;
    int retryIndex = 0;
    while (true) {
      try {
        modified = modificationFunction.apply(primary);
        modified.getMetadata().setResourceVersion(primary.getMetadata().getResourceVersion());
        var updated = updateMethod.apply(modified);
        context
            .eventSourceRetriever()
            .getControllerEventSource()
            .handleRecentResourceUpdate(ResourceID.fromResource(primary), updated, primary);
        return updated;
      } catch (KubernetesClientException e) {
        log.trace("Exception during patch for resource: {}", primary);
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
        if (retryIndex >= maxRetry) {
          log.warn("Retry exhausted, last desired resource: {}", modified);
          throw new OperatorException(
              "Exceeded maximum ("
                  + maxRetry
                  + ") retry attempts to patch resource: "
                  + ResourceID.fromResource(primary));
        }
        log.debug(
            "Retrying patch for resource name: {}, namespace: {}; HTTP code: {}",
            primary.getMetadata().getName(),
            primary.getMetadata().getNamespace(),
            e.getCode());
        primary =
            (P)
                context
                    .getClient()
                    .resources(primary.getClass())
                    .inNamespace(primary.getMetadata().getNamespace())
                    .withName(primary.getMetadata().getName())
                    .get();
      }
    }
  }
}
