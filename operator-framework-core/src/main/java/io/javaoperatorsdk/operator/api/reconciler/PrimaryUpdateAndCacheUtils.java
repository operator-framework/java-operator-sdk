package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

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
  public static final int DEFAULT_RESOURCE_CACHE_TIMEOUT_MILLIS = 10000;
  public static final int DEFAULT_RESOURCE_CACHE_POLL_PERIOD_MILLIS = 50;

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
  public static <P extends HasMetadata> P mergePatchStatusAndCacheResource(
      P primary, Context<P> context, UnaryOperator<P> modificationFunction) {
    return updateAndCacheResource(
        primary, context, modificationFunction, r -> context.getClient().resource(r).patchStatus());
  }

  /**
   * Patches the status using JSON Patch with optimistic locking and caches the result for next
   * reconciliation. For details see {@link #updateAndCacheResource}.
   */
  public static <P extends HasMetadata> P patchStatusAndCacheResource(
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
   * Same as {@link #updateAndCacheResource(HasMetadata, Context, UnaryOperator, UnaryOperator, int,
   * long,long)} using the default maximum retry number as defined by {@link #DEFAULT_MAX_RETRY} and
   * default cache maximum polling time and period as defined, respectively by {@link
   * #DEFAULT_RESOURCE_CACHE_TIMEOUT_MILLIS} and {@link #DEFAULT_RESOURCE_CACHE_POLL_PERIOD_MILLIS}.
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
        resourceToUpdate,
        context,
        modificationFunction,
        updateMethod,
        DEFAULT_MAX_RETRY,
        DEFAULT_RESOURCE_CACHE_TIMEOUT_MILLIS,
        DEFAULT_RESOURCE_CACHE_POLL_PERIOD_MILLIS);
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
   * @param cachePollTimeoutMillis maximum amount of milliseconds to wait for the updated resource
   *     to appear in cache
   * @param cachePollPeriodMillis cache polling period, in milliseconds
   * @param <P> primary type
   * @return the updated resource
   */
  public static <P extends HasMetadata> P updateAndCacheResource(
      P resourceToUpdate,
      Context<P> context,
      UnaryOperator<P> modificationFunction,
      UnaryOperator<P> updateMethod,
      int maxRetry,
      long cachePollTimeoutMillis,
      long cachePollPeriodMillis) {

    if (log.isDebugEnabled()) {
      log.debug("Update and cache: {}", ResourceID.fromResource(resourceToUpdate));
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
            pollLocalCache(
                context, resourceToUpdate, cachePollTimeoutMillis, cachePollPeriodMillis);
      }
    }
  }

  private static <P extends HasMetadata> P pollLocalCache(
      Context<P> context, P staleResource, long timeoutMillis, long pollDelayMillis) {
    try {
      var resourceId = ResourceID.fromResource(staleResource);
      var startTime = LocalTime.now();
      final var timeoutTime = startTime.plus(timeoutMillis, ChronoUnit.MILLIS);
      while (timeoutTime.isAfter(LocalTime.now())) {
        log.debug("Polling cache for resource: {}", resourceId);
        var cachedResource = context.getPrimaryCache().get(resourceId).orElseThrow();
        if (!cachedResource
            .getMetadata()
            .getResourceVersion()
            .equals(staleResource.getMetadata().getResourceVersion())) {
          return context
              .getControllerConfiguration()
              .getConfigurationService()
              .getResourceCloner()
              .clone(cachedResource);
        }
        Thread.sleep(pollDelayMillis);
      }
      throw new OperatorException("Timeout of resource polling from cache for resource");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OperatorException(e);
    }
  }

  /**
   * Adds finalizer to the primary resource from the context using JSON Patch. Retries conflicts and
   * unprocessable content (HTTP 422), see {@link
   * PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient, HasMetadata, UnaryOperator,
   * Predicate)} for details on retry. It does not add finalizer if there is already a finalizer or
   * resource is marked for deletion.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizer(Context<P> context, String finalizer) {
    return addFinalizer(context.getClient(), context.getPrimaryResource(), finalizer);
  }

  /**
   * Adds finalizer to the resource using JSON Patch. Retries conflicts and unprocessable content
   * (HTTP 422), see {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient,
   * HasMetadata, UnaryOperator, Predicate)} for details on retry. It does not try to add finalizer
   * if there is already a finalizer or resource is marked for deletion.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizer(
      KubernetesClient client, P resource, String finalizerName) {
    if (resource.isMarkedForDeletion() || resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatch(
        client,
        resource,
        r -> {
          r.addFinalizer(finalizerName);
          return r;
        },
        r -> !r.hasFinalizer(finalizerName));
  }

  /**
   * Removes the target finalizer from the primary resource from the Context. Uses JSON Patch and
   * handles retries, see {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient,
   * HasMetadata, UnaryOperator, Predicate)} for details. It does not try to remove finalizer if
   * finalizer is not present on the resource.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P removeFinalizer(
      Context<P> context, String finalizerName) {
    return removeFinalizer(context.getClient(), context.getPrimaryResource(), finalizerName);
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
      KubernetesClient client, P resource, String finalizerName) {
    if (!resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatch(
        client,
        resource,
        r -> {
          r.removeFinalizer(finalizerName);
          return r;
        },
        r -> r.hasFinalizer(finalizerName));
  }

  /**
   * Patches the resource using JSON Patch. In case the server responds with conflict (HTTP 409) or
   * unprocessable content (HTTP 422) it retries the operation up to the maximum number defined in
   * {@link PrimaryUpdateAndCacheUtils#DEFAULT_MAX_RETRY}.
   *
   * @param client KubernetesClient
   * @param resource to update
   * @param resourceChangesOperator changes to be done on the resource before update
   * @param preCondition condition to check if the patch operation still needs to be performed or
   *     not.
   * @return updated resource from the server or unchanged if the precondition does not hold.
   * @param <P> resource type
   */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P conflictRetryingPatch(
      KubernetesClient client,
      P resource,
      UnaryOperator<P> resourceChangesOperator,
      Predicate<P> preCondition) {
    if (log.isDebugEnabled()) {
      log.debug("Conflict retrying update for: {}", ResourceID.fromResource(resource));
    }
    int retryIndex = 0;
    while (true) {
      try {
        if (!preCondition.test(resource)) {
          return resource;
        }
        return client.resource(resource).edit(resourceChangesOperator);
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
   * Adds finalizer using Server-Side Apply. In the background this method creates a fresh copy of
   * the target resource, setting only name, namespace and finalizer. Does not use optimistic
   * locking for the patch.
   *
   * @return the patched resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizerWithSSA(
      Context<P> context, P originalResource, String finalizerName) {
    return addFinalizerWithSSA(
        context.getClient(),
        originalResource,
        finalizerName,
        context.getControllerConfiguration().fieldManager());
  }

  /**
   * Adds finalizer using Server-Side Apply. In the background this method creates a fresh copy of
   * the target resource, setting only name, namespace and finalizer. Does not use optimistic
   * locking for the patch.
   *
   * @return the patched resource from the server response
   */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P addFinalizerWithSSA(
      KubernetesClient client, P originalResource, String finalizerName, String fieldManager) {
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
      return client
          .resource(resource)
          .patch(
              new PatchContext.Builder()
                  .withFieldManager(fieldManager)
                  .withForce(true)
                  .withPatchType(PatchType.SERVER_SIDE_APPLY)
                  .build());
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
