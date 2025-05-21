package io.javaoperatorsdk.operator.baseapi.statuscache;

import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class StatusPatchCacheReconciler implements Reconciler<StatusPatchCacheCustomResource> {

  public volatile int latestValue = 0;
  public volatile boolean errorPresent = false;

  @Override
  public UpdateControl<StatusPatchCacheCustomResource> reconcile(
      StatusPatchCacheCustomResource resource, Context<StatusPatchCacheCustomResource> context) {

    if (resource.getStatus() != null && resource.getStatus().getValue() != latestValue) {
      errorPresent = true;
      throw new IllegalStateException(
          "status is not up to date. Latest value: "
              + latestValue
              + " status values: "
              + resource.getStatus().getValue());
    }

    // test also resource update happening meanwhile reconciliation
    resource.getSpec().setCounter(resource.getSpec().getCounter() + 1);
    context.getClient().resource(resource).update();

    var freshCopy = createFreshCopy(resource);

    freshCopy
        .getStatus()
        .setValue(resource.getStatus() == null ? 1 : resource.getStatus().getValue() + 1);

    var updated =
        PrimaryUpdateAndCacheUtils.ssaPatchStatusAndCacheResource(resource, freshCopy, context);
    latestValue = updated.getStatus().getValue();

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, StatusPatchCacheCustomResource>> prepareEventSources(
      EventSourceContext<StatusPatchCacheCustomResource> context) {
    // periodic event triggering for testing purposes
    return List.of(new PeriodicTriggerEventSource<>(context.getPrimaryCache()));
  }

  private StatusPatchCacheCustomResource createFreshCopy(StatusPatchCacheCustomResource resource) {
    var res = new StatusPatchCacheCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setStatus(new StatusPatchCacheStatus());
    return res;
  }
}
