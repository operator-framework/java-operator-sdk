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

  /** Adds finalizer using JSON Patch. Retries conflicts and unprocessable content (HTTP 422) */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P addFinalizer(
      KubernetesClient client, P resource, String finalizerName) {
    return conflictRetryingPatch(
        client,
        resource,
        r -> {
          r.addFinalizer(finalizerName);
          return r;
        },
        r -> !r.hasFinalizer(finalizerName));
  }

  public static <P extends HasMetadata> P removeFinalizer(
      KubernetesClient client, P resource, String finalizerName) {
    return conflictRetryingPatch(
        client,
        resource,
        r -> {
          r.removeFinalizer(finalizerName);
          return r;
        },
        r -> r.hasFinalizer(finalizerName));
  }

  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P conflictRetryingPatch(
      KubernetesClient client,
      P resource,
      UnaryOperator<P> unaryOperator,
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
        return client.resource(resource).edit(unaryOperator);
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

  /** Adds finalizer using Server-Side Apply. */
  public static <P extends HasMetadata> P addFinalizerWithSSA(
      Context<P> context, P originalResource, String finalizerName) {
    return addFinalizerWithSSA(
        context.getClient(),
        originalResource,
        finalizerName,
        context.getControllerConfiguration().fieldManager());
  }

  /** Adds finalizer using Server-Side Apply. */
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

  /**
   * Experimental. Patches finalizer. For retry uses informer cache to get the fresh resources,
   * therefore makes less Kubernetes API Calls.
   */
  @Experimental(
      "Not used internally for now. Therefor we don't consider it well tested. But the intention is"
          + " to have it as default in the future.")
  public static <P extends HasMetadata> P addFinalizer(
      P resource, String finalizer, Context<P> context) {

    if (resource.hasFinalizer(finalizer)) {
      log.debug("Skipping adding finalizer, since already present.");
      return resource;
    }

    return updateAndCacheResource(
        resource,
        context,
        r -> r,
        r ->
            context
                .getClient()
                .resource(r)
                .edit(
                    res -> {
                      res.addFinalizer(finalizer);
                      return res;
                    }));
  }

  /**
   * Experimental. Removes finalizer, for retry uses informer cache to get the fresh resources,
   * therefore makes less Kubernetes API Calls.
   */
  @Experimental(
      "Not used internally for now. Therefor we don't consider it well tested. But the intention is"
          + " to have it as default in the future.")
  public static <P extends HasMetadata> P removeFinalizer(
      P resource, String finalizer, Context<P> context) {
    if (!resource.hasFinalizer(finalizer)) {
      log.debug("Skipping removing finalizer, since not present.");
      return resource;
    }
    return updateAndCacheResource(
        resource,
        context,
        r -> r,
        r ->
            context
                .getClient()
                .resource(r)
                .edit(
                    res -> {
                      res.removeFinalizer(finalizer);
                      return res;
                    }));
  }
}
