package io.javaoperatorsdk.operator.baseapi.statuscache.primarycache;

import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.support.PrimaryResourceCache;
import io.javaoperatorsdk.operator.baseapi.statuscache.PeriodicTriggerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class StatusPatchPrimaryCacheReconciler
    implements Reconciler<StatusPatchPrimaryCacheCustomResource>,
        Cleaner<StatusPatchPrimaryCacheCustomResource> {

  public volatile int latestValue = 0;
  public volatile boolean errorPresent = false;

  // We on purpose don't use the provided predicate to show what a custom one could look like.
  private final PrimaryResourceCache<StatusPatchPrimaryCacheCustomResource> cache =
      new PrimaryResourceCache<>(
          (statusPatchCacheCustomResourcePair, statusPatchCacheCustomResource) ->
              statusPatchCacheCustomResource.getStatus().getValue()
                  >= statusPatchCacheCustomResourcePair.afterUpdate().getStatus().getValue());

  @Override
  public UpdateControl<StatusPatchPrimaryCacheCustomResource> reconcile(
      StatusPatchPrimaryCacheCustomResource primary,
      Context<StatusPatchPrimaryCacheCustomResource> context) {

    primary = cache.getFreshResource(primary);

    if (primary.getStatus() != null && primary.getStatus().getValue() != latestValue) {
      errorPresent = true;
      throw new IllegalStateException(
          "status is not up to date. Latest value: "
              + latestValue
              + " status values: "
              + primary.getStatus().getValue());
    }

    // test also resource update happening meanwhile reconciliation
    primary.getSpec().setCounter(primary.getSpec().getCounter() + 1);
    context.getClient().resource(primary).update();

    var freshCopy = createFreshCopy(primary);
    freshCopy
        .getStatus()
        .setValue(primary.getStatus() == null ? 1 : primary.getStatus().getValue() + 1);

    var updated =
        PrimaryUpdateAndCacheUtils.ssaPatchStatusAndCacheResource(
            primary, freshCopy, context, cache);
    latestValue = updated.getStatus().getValue();

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, StatusPatchPrimaryCacheCustomResource>> prepareEventSources(
      EventSourceContext<StatusPatchPrimaryCacheCustomResource> context) {
    // periodic event triggering for testing purposes
    return List.of(new PeriodicTriggerEventSource<>(context.getPrimaryCache()));
  }

  private StatusPatchPrimaryCacheCustomResource createFreshCopy(
      StatusPatchPrimaryCacheCustomResource resource) {
    var res = new StatusPatchPrimaryCacheCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setStatus(new StatusPatchPrimaryCacheStatus());

    return res;
  }

  @Override
  public DeleteControl cleanup(
      StatusPatchPrimaryCacheCustomResource resource,
      Context<StatusPatchPrimaryCacheCustomResource> context)
      throws Exception {
    cache.cleanup(resource);
    return DeleteControl.defaultDelete();
  }
}
