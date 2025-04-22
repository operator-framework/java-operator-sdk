package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.reconciler.support.PrimaryResourceCache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class PrimaryUpdateAndCacheUtils {

  private PrimaryUpdateAndCacheUtils() {}

  private static final Logger log = LoggerFactory.getLogger(PrimaryUpdateAndCacheUtils.class);

  /**
   * Makes sure that the up-to-date primary resource will be present during the next reconciliation.
   * Using update (PUT) method.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P updateAndCacheStatusWith(P primary, Context<P> context) {
    return patchAndCacheStatusWith(primary, context, (p, c) -> c.resource(primary).updateStatus());
  }

  /**
   * Makes sure that the up-to-date primary resource will be present during the next reconciliation.
   * Using JSON Merge patch.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchAndCacheStatusWith(P primary, Context<P> context) {
    return patchAndCacheStatusWith(primary, context, (p, c) -> c.resource(primary).patchStatus());
  }

  /**
   * Makes sure that the up-to-date primary resource will be present during the next reconciliation.
   * Using JSON Patch.
   *
   * @param primary resource
   * @param context of reconciliation
   * @return updated resource
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P editAndCacheStatusWith(
      P primary, Context<P> context, UnaryOperator<P> operation) {
    return patchAndCacheStatusWith(
        primary, context, (p, c) -> c.resource(primary).editStatus(operation));
  }

  /**
   * Makes sure that the up-to-date primary resource will be present during the next reconciliation.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param patch free implementation of cache - make sure you use optimistic locking during the
   *     update
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchAndCacheStatusWith(
      P primary, Context<P> context, BiFunction<P, KubernetesClient, P> patch) {
    var updatedResource = patch.apply(primary, context.getClient());
    context
        .eventSourceRetriever()
        .getControllerEventSource()
        .handleRecentResourceUpdate(ResourceID.fromResource(primary), updatedResource, primary);
    return updatedResource;
  }

  /**
   * Makes sure that the up-to-date primary resource will be present during the next reconciliation.
   * Using Server Side Apply.
   *
   * @param primary resource
   * @param freshResourceWithStatus - fresh resource with target state
   * @param context of reconciliation
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P ssaPatchAndCacheStatusWith(
      P primary, P freshResourceWithStatus, Context<P> context) {
    var res =
        context
            .getClient()
            .resource(freshResourceWithStatus)
            .subresource("status")
            .patch(
                new PatchContext.Builder()
                    .withForce(true)
                    .withFieldManager(context.getControllerConfiguration().fieldManager())
                    .withPatchType(PatchType.SERVER_SIDE_APPLY)
                    .build());

    context
        .eventSourceRetriever()
        .getControllerEventSource()
        .handleRecentResourceUpdate(ResourceID.fromResource(primary), res, primary);
    return res;
  }

  /**
   * Patches the resource and adds it to the {@link PrimaryResourceCache} provided. Optimistic
   * locking is not required.
   *
   * @param primary resource
   * @param freshResourceWithStatus - fresh resource with target state
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P ssaPatchAndCacheStatus(
      P primary, P freshResourceWithStatus, Context<P> context, PrimaryResourceCache<P> cache) {
    logWarnIfResourceVersionPresent(freshResourceWithStatus);
    return patchAndCacheStatus(
        primary,
        context.getClient(),
        cache,
        (P p, KubernetesClient c) ->
            c.resource(freshResourceWithStatus)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()));
  }

  /**
   * Patches the resource with JSON Patch and adds it to the {@link PrimaryResourceCache} provided.
   * Optimistic locking is not required.
   *
   * @param primary resource*
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P editAndCacheStatus(
      P primary, Context<P> context, PrimaryResourceCache<P> cache, UnaryOperator<P> operation) {
    logWarnIfResourceVersionPresent(primary);
    return patchAndCacheStatus(
        primary,
        context.getClient(),
        cache,
        (P p, KubernetesClient c) -> c.resource(primary).editStatus(operation));
  }

  /**
   * Patches the resource with JSON Merge patch and adds it to the {@link PrimaryResourceCache}
   * provided. Optimistic locking is not required.
   *
   * @param primary resource*
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchAndCacheStatus(
      P primary, Context<P> context, PrimaryResourceCache<P> cache) {
    logWarnIfResourceVersionPresent(primary);
    return patchAndCacheStatus(
        primary,
        context.getClient(),
        cache,
        (P p, KubernetesClient c) -> c.resource(primary).patchStatus());
  }

  /**
   * Updates the resource and adds it to the {@link PrimaryResourceCache} provided. Optimistic
   * locking is not required.
   *
   * @param primary resource*
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P updateAndCacheStatus(
      P primary, Context<P> context, PrimaryResourceCache<P> cache) {
    logWarnIfResourceVersionPresent(primary);
    return patchAndCacheStatus(
        primary,
        context.getClient(),
        cache,
        (P p, KubernetesClient c) -> c.resource(primary).updateStatus());
  }

  public static <P extends HasMetadata> P patchAndCacheStatus(
      P primary,
      KubernetesClient client,
      PrimaryResourceCache<P> cache,
      BiFunction<P, KubernetesClient, P> patch) {
    var updatedResource = patch.apply(primary, client);
    cache.cacheResource(primary, updatedResource);
    return updatedResource;
  }

  private static <P extends HasMetadata> void logWarnIfResourceVersionPresent(P primary) {
    if (primary.getMetadata().getResourceVersion() != null) {
      log.warn(
          "Primary resource version is NOT null, for caching with optimistic locking use"
              + " alternative methods. Name: {} namespace: {}",
          primary.getMetadata().getName(),
          primary.getMetadata().getNamespace());
    }
  }
}
