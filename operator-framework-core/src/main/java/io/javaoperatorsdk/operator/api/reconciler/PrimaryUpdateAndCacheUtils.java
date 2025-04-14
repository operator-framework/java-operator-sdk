package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.reconciler.support.UserPrimaryResourceCache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class PrimaryUpdateAndCacheUtils {

  private PrimaryUpdateAndCacheUtils() {}

  private static final Logger log = LoggerFactory.getLogger(PrimaryUpdateAndCacheUtils.class);

  public static <P extends HasMetadata> P updateAndCacheStatus(P primary, Context<P> context) {
    return patchAndCacheStatusWithLock(
        primary, context, (p, c) -> c.resource(primary).updateStatus());
  }

  public static <P extends HasMetadata> P patchAndCacheStatusWithLock(
      P primary, Context<P> context) {
    return patchAndCacheStatusWithLock(
        primary, context, (p, c) -> c.resource(primary).patchStatus());
  }

  public static <P extends HasMetadata> P editAndCacheStatusWithLock(
      P primary, Context<P> context, UnaryOperator<P> operation) {
    return patchAndCacheStatusWithLock(
        primary, context, (p, c) -> c.resource(primary).editStatus(operation));
  }

  public static <P extends HasMetadata> P patchAndCacheStatusWithLock(
      P primary, Context<P> context, BiFunction<P, KubernetesClient, P> patch) {
    checkResourceVersionPresent(primary);
    var updatedResource = patch.apply(primary, context.getClient());
    context
        .eventSourceRetriever()
        .getControllerEventSource()
        .handleRecentResourceUpdate(ResourceID.fromResource(primary), updatedResource, primary);
    return null;
  }

  public static <P extends HasMetadata> P ssaPatchAndCacheStatusWithLock(
      P primary, P freshResourceWithStatus, Context<P> context) {
    checkResourceVersionPresent(freshResourceWithStatus);
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

  public static <P extends HasMetadata> P ssaPatchAndCacheStatus(
      P primary, P freshResource, Context<P> context, UserPrimaryResourceCache<P> cache) {
    logWarnIfResourceVersionPresent(freshResource);
    return patchAndCacheStatus(
        primary,
        context.getClient(),
        cache,
        (P p, KubernetesClient c) ->
            c.resource(freshResource)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()));
  }

  public static <P extends HasMetadata> P patchAndCacheStatus(
      P primary,
      KubernetesClient client,
      UserPrimaryResourceCache<P> cache,
      BiFunction<P, KubernetesClient, P> patch) {
    var updatedResource = patch.apply(primary, client);
    cache.cacheResource(primary, updatedResource);
    return updatedResource;
  }

  private static <P extends HasMetadata> void checkResourceVersionPresent(P primary) {
    if (primary.getMetadata().getResourceVersion() == null) {
      throw new IllegalStateException(
          "Primary resource version is null, it is expected to set resource version for updates for caching. Name: %s namespace: %s"
              .formatted(primary.getMetadata().getName(), primary.getMetadata().getNamespace()));
    }
  }

  private static <P extends HasMetadata> void logWarnIfResourceVersionPresent(P primary) {
    if (primary.getMetadata().getResourceVersion() != null) {
      log.warn(
          "Primary resource version is NOT  null, for caching with optimistic locking use"
              + " alternative methods. Name: {} namespace: {}",
          primary.getMetadata().getName(),
          primary.getMetadata().getNamespace());
    }
  }
}
