package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.support.PrimaryResourceCache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Utility methods to patch the primary resource state and store it to the related cache, to make
 * sure that fresh resource is present for the next reconciliation. The main use case for such
 * updates is to store state is resource status. Use of optimistic locking is not desired for such
 * updates, since we don't want to patch fail and lose information that we want to store.
 */
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
  public static <P extends HasMetadata> P updateAndCacheStatus(P primary, Context<P> context) {
    sanityChecks(primary, context);
    return patchAndCacheStatus(
        primary, context, () -> context.getClient().resource(primary).updateStatus());
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
  public static <P extends HasMetadata> P patchAndCacheStatus(P primary, Context<P> context) {
    sanityChecks(primary, context);
    return patchAndCacheStatus(
        primary, context, () -> context.getClient().resource(primary).patchStatus());
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
  public static <P extends HasMetadata> P editAndCacheStatus(
      P primary, Context<P> context, UnaryOperator<P> operation) {
    sanityChecks(primary, context);
    return patchAndCacheStatus(
        primary, context, () -> context.getClient().resource(primary).editStatus(operation));
  }

  /**
   * Makes sure that the up-to-date primary resource will be present during the next reconciliation.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param patch free implementation of cache
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchAndCacheStatus(
      P primary, Context<P> context, Supplier<P> patch) {
    var updatedResource = patch.get();
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
  public static <P extends HasMetadata> P ssaPatchAndCacheStatus(
      P primary, P freshResourceWithStatus, Context<P> context) {
    sanityChecks(freshResourceWithStatus, context);
    return patchAndCacheStatus(
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
   * Patches the resource and adds it to the {@link PrimaryResourceCache}.
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
    sanityChecks(freshResourceWithStatus, context);
    return patchAndCacheStatus(
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
   * Patches the resource with JSON Patch and adds it to the {@link PrimaryResourceCache}.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P editAndCacheStatus(
      P primary, Context<P> context, PrimaryResourceCache<P> cache, UnaryOperator<P> operation) {
    sanityChecks(primary, context);
    return patchAndCacheStatus(
        primary, cache, () -> context.getClient().resource(primary).editStatus(operation));
  }

  /**
   * Patches the resource with JSON Merge patch and adds it to the {@link PrimaryResourceCache}
   * provided.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchAndCacheStatus(
      P primary, Context<P> context, PrimaryResourceCache<P> cache) {
    sanityChecks(primary, context);
    return patchAndCacheStatus(
        primary, cache, () -> context.getClient().resource(primary).patchStatus());
  }

  /**
   * Updates the resource and adds it to the {@link PrimaryResourceCache}.
   *
   * @param primary resource
   * @param context of reconciliation
   * @param cache - resource cache managed by user
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P updateAndCacheStatus(
      P primary, Context<P> context, PrimaryResourceCache<P> cache) {
    sanityChecks(primary, context);
    return patchAndCacheStatus(
        primary, cache, () -> context.getClient().resource(primary).updateStatus());
  }

  /**
   * Updates the resource using the user provided implementation anc caches the result.
   *
   * @param primary resource
   * @param cache resource cache managed by user
   * @param patch implementation of resource update*
   * @return the updated resource.
   * @param <P> primary resource type
   */
  public static <P extends HasMetadata> P patchAndCacheStatus(
      P primary, PrimaryResourceCache<P> cache, Supplier<P> patch) {
    var updatedResource = patch.get();
    cache.cacheResource(primary, updatedResource);
    return updatedResource;
  }

  private static <P extends HasMetadata> void sanityChecks(P primary, Context<P> context) {
    if (primary.getMetadata().getResourceVersion() != null) {
      log.warn(
          "The metadata.resourceVersion of primary resource is NOT null, "
              + "using optimistic locking is discouraged for this purpose. ");
    }
    if (!context
        .getControllerConfiguration()
        .getConfigurationService()
        .parseResourceVersionsForEventFilteringAndCaching()) {
      throw new OperatorException(
          "For internal primary resource caching 'parseResourceVersionsForEventFilteringAndCaching'"
              + " must be allowed.");
    }
  }
}
