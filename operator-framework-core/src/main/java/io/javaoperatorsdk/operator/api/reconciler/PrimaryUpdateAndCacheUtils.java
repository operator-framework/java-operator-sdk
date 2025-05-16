package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Utility methods to patch the primary resource state and store it to the related cache, to make
 * sure that the latest version of the resource is present for the next reconciliation. The main use
 * case for such updates is to store state is resource status.
 *
 * <p>The way the framework handles this is with retryable updates with optimistic locking, and
 * caches the updated resource from the response in an overlay cache on top of the Informer cache.
 * If the update fails, it reads the primary resource from the cluster, applies the modifications
 * again and retries the update.
 */
public class PrimaryUpdateAndCacheUtils {

  public static final int DEFAULT_MAX_RETRY = 10;

  private PrimaryUpdateAndCacheUtils() {}

  private static final Logger log = LoggerFactory.getLogger(PrimaryUpdateAndCacheUtils.class);

  /**
   * Updates the status with optimistic locking and caches the result for next reconciliation. For
   * details see {@link #updateAndCacheResource}.
   */
  public static <P extends HasMetadata> P updateStatusAndCacheResource(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResource(
        primary,
        context,
        modificationFunction,
        r -> context.getClient().resource(r).updateStatus());
  }

  /**
   * Patches the status using JSON Merge Patch with optimistic locking and caches the result for
   * next reconciliation. For details see {@link #updateAndCacheResource}.
   */
  public static <P extends HasMetadata> P jsonMergePatchStatusAndCacheResource(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResource(
        primary, context, modificationFunction, r -> context.getClient().resource(r).patchStatus());
  }

  /**
   * Patches the status using JSON Patch with optimistic locking and caches the result for next
   * reconciliation. For details see {@link #updateAndCacheResource}.
   */
  public static <P extends HasMetadata> P jsonPatchStatusAndCacheResource(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResource(
        primary,
        context,
        UnaryOperator.identity(),
        r -> context.getClient().resource(r).editStatus(modificationFunction));
  }

  /**
   * Patches the status using Server Side Apply with optimistic locking and caches the result for
   * next reconciliation. For details see {@link #updateAndCacheResource}.
   */
  public static <P extends HasMetadata> P ssaPatchStatusAndCacheResource(
      P primary, P freshResourceWithStatus, Context<P> context) {
    return updateAndCacheResource(
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
   * Same as {@link #updateAndCacheResource(HasMetadata, Context, UnaryOperator, UnaryOperator,
   * int)} using the default maximum retry number as defined by {@link #DEFAULT_MAX_RETRY}.
   *
   * @param resourceToUpdate original resource to update
   * @param context of reconciliation
   * @param modificationFunction modifications to make on primary
   * @param updateMethod the update method implementation
   * @param <P> primary type
   * @return the updated resource
   */
  public static <P extends HasMetadata> P updateAndCacheResource(
      P resourceToUpdate,
      Context<P> context,
      UnaryOperator<P> modificationFunction,
      UnaryOperator<P> updateMethod) {
    return updateAndCacheResource(
        resourceToUpdate, context, modificationFunction, updateMethod, DEFAULT_MAX_RETRY);
  }

  /**
   * Modifies the primary using the specified modification function, then uses the modified resource
   * for the request to update with provided update method. As the {@code resourceVersion} field of
   * the modified resource is set to the value found in the specified resource to update, the update
   * operation will therefore use optimistic locking on the server. If the request fails on
   * optimistic update, we read the resource again from the K8S API server and retry the whole
   * process. In short, we make sure we always update the resource with optimistic locking, then we
   * cache the resource in an internal cache. Without further going into details, the optimistic
   * locking is needed so we can reliably handle the caching.
   *
   * @param resourceToUpdate original resource to update
   * @param context of reconciliation
   * @param modificationFunction modifications to make on primary
   * @param updateMethod the update method implementation
   * @param maxRetry maximum number of retries before giving up
   * @param <P> primary type
   * @return the updated resource
   */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P updateAndCacheResource(
      P resourceToUpdate,
      Context<P> context,
      UnaryOperator<P> modificationFunction,
      UnaryOperator<P> updateMethod,
      int maxRetry) {

    if (log.isDebugEnabled()) {
      log.debug("Conflict retrying update for: {}", ResourceID.fromResource(resourceToUpdate));
    }
    P modified = null;
    int retryIndex = 0;
    while (true) {
      try {
        modified = modificationFunction.apply(resourceToUpdate);
        modified
            .getMetadata()
            .setResourceVersion(resourceToUpdate.getMetadata().getResourceVersion());
        var updated = updateMethod.apply(modified);
        context
            .eventSourceRetriever()
            .getControllerEventSource()
            .handleRecentResourceUpdate(
                ResourceID.fromResource(resourceToUpdate), updated, resourceToUpdate);
        return updated;
      } catch (KubernetesClientException e) {
        log.trace("Exception during patch for resource: {}", resourceToUpdate);
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
        if (retryIndex > maxRetry) {
          log.warn("Retry exhausted, last desired resource: {}", modified);
          throw new OperatorException(
              "Exceeded maximum ("
                  + maxRetry
                  + ") retry attempts to patch resource: "
                  + ResourceID.fromResource(resourceToUpdate),
              e);
        }
        log.debug(
            "Retrying patch for resource name: {}, namespace: {}; HTTP code: {}",
            resourceToUpdate.getMetadata().getName(),
            resourceToUpdate.getMetadata().getNamespace(),
            e.getCode());
        resourceToUpdate =
            (P)
                context
                    .getClient()
                    .resources(resourceToUpdate.getClass())
                    .inNamespace(resourceToUpdate.getMetadata().getNamespace())
                    .withName(resourceToUpdate.getMetadata().getName())
                    .get();
      }
    }
  }
}
