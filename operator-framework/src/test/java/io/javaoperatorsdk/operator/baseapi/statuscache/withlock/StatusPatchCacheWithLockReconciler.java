package io.javaoperatorsdk.operator.baseapi.statuscache.withlock;

import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.baseapi.statuscache.PeriodicTriggerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class StatusPatchCacheWithLockReconciler
    implements Reconciler<StatusPatchCacheWithLockCustomResource> {

  public volatile int latestValue = 0;
  public volatile boolean errorPresent = false;

  @Override
  public UpdateControl<StatusPatchCacheWithLockCustomResource> reconcile(
      StatusPatchCacheWithLockCustomResource resource,
      Context<StatusPatchCacheWithLockCustomResource> context)
      throws InterruptedException {

    if (resource.getStatus() != null && resource.getStatus().getValue() != latestValue) {
      errorPresent = true;
      throw new IllegalStateException(
          "status is not up to date. Latest value: "
              + latestValue
              + " status values: "
              + resource.getStatus().getValue());
    }

    var freshCopy = createFreshCopy(resource);
    // setting the resource version
    freshCopy.getMetadata().setResourceVersion(resource.getMetadata().getResourceVersion());
    freshCopy
        .getStatus()
        .setValue(resource.getStatus() == null ? 1 : resource.getStatus().getValue() + 1);

    resource.getMetadata().setResourceVersion(null);
    var updated =
        PrimaryUpdateAndCacheUtils.ssaPatchAndCacheStatusWith(resource, freshCopy, context);
    latestValue = updated.getStatus().getValue();

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, StatusPatchCacheWithLockCustomResource>> prepareEventSources(
      EventSourceContext<StatusPatchCacheWithLockCustomResource> context) {
    // periodic event triggering for testing purposes
    return List.of(new PeriodicTriggerEventSource<>(context.getPrimaryCache()));
  }

  private StatusPatchCacheWithLockCustomResource createFreshCopy(
      StatusPatchCacheWithLockCustomResource resource) {
    var res = new StatusPatchCacheWithLockCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setStatus(new StatusPatchCacheWithLockStatus());

    return res;
  }
}
