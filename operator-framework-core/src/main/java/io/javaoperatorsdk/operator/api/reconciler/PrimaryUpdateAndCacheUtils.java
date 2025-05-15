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
 * sure that fresh resource is present for the next reconciliation. The main use case for such
 * updates is to store state is resource status.
 *
 * <p>The way the framework handles this is with retryable updates with optimistic locking, and
 * caches the updated resource from the response in an overlay cache on top of the Informer behind.
 * If the update fails, it reads the primary resource and applies the modifications again and
 * retries the update.
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
  public static <P extends HasMetadata> P patchStatusAndCacheResource(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResource(
        primary, context, modificationFunction, r -> context.getClient().resource(r).patchStatus());
  }

  /**
   * Patches the status using JSON Patch with optimistic locking and caches the result for next
   * reconciliation. For details see {@link #updateAndCacheResource}.
   */
  public static <P extends HasMetadata> P editStatusAndCacheResource(
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
  public static <P extends HasMetadata> P updateAndCacheResource(
      P primary,
      Context<P> context,
      UnaryOperator<P> modificationFunction,
      UnaryOperator<P> updateMethod) {
    return updateAndCacheResource(
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
  public static <P extends HasMetadata> P updateAndCacheResource(
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
