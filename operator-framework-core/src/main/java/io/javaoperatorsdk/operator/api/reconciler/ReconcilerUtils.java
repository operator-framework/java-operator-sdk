package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

public class ReconcilerUtils {
  // toto namespace handling
  // todo compare resource version if multiple event sources provide the same resource
  // for json patch  make sense to retry (json merge patch?)

  public static <R extends HasMetadata> R ssa(Context<? extends HasMetadata> context, R resource) {
    return handleResourcePatch(
        context,
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
                        .build()));
  }

  public static <R extends HasMetadata> R ssaStatus(
      Context<? extends HasMetadata> context, R resource) {
    return handleResourcePatch(
        context,
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
                        .build()));
  }

  public static <P extends HasMetadata> P ssaPrimary(Context<P> context, P resource) {
    return handleResourcePatch(
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
        true,
        context.eventSourceRetriever().getControllerEventSource());
  }

  public static <P extends HasMetadata> P ssaStatusPrimary(Context<P> context, P resource) {
    return handleResourcePatch(
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
        true,
        context.eventSourceRetriever().getControllerEventSource());
  }

  public static <R extends HasMetadata> R handleResourcePatch(
      Context<?> context, R resource, UnaryOperator<R> updateOperation) {
    var esList = context.eventSourceRetriever().getEventSourcesFor(resource.getClass());
    if (esList.isEmpty()) {
      throw new IllegalStateException("No event source found for type: " + resource.getClass());
    }
    if (esList.size() > 1) {
      throw new IllegalStateException(
          "Multiple event sources found for: "
              + resource.getClass()
              + " please provide the target event source");
    }
    var es = esList.get(0);
    if (es instanceof ManagedInformerEventSource mes) {
      return handleResourcePatch(resource, updateOperation, true, mes);
    } else {
      throw new IllegalStateException(
          "Target event source must be a subclass off "
              + ManagedInformerEventSource.class.getName());
    }
  }

  @SuppressWarnings("unchecked")
  private static <R extends HasMetadata> R handleResourcePatch(
      R resource,
      UnaryOperator<R> updateOperation,
      boolean doNotLock,
      ManagedInformerEventSource ies) {
    var resourceVersion = resource.getMetadata().getResourceVersion();
    try {
      if (resourceVersion != null && doNotLock) {
        resource.getMetadata().setResourceVersion(null);
      }
      return (R) ies.updateAndCacheResource(resource, updateOperation);
    } finally {
      if (resourceVersion != null && doNotLock) {
        resource.getMetadata().setResourceVersion(resourceVersion);
      }
    }
  }
}
