package io.javaoperatorsdk.operator.baseapi.statuscache.updatecontrol;

import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.baseapi.statuscache.PeriodicTriggerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class UpdateControlPrimaryReconciler
    implements Reconciler<UpdateControlPrimaryCacheCustomResource> {

  public volatile int latestValue = 0;
  public volatile boolean errorPresent = false;

  @Override
  public UpdateControl<UpdateControlPrimaryCacheCustomResource> reconcile(
      UpdateControlPrimaryCacheCustomResource resource,
      Context<UpdateControlPrimaryCacheCustomResource> context) {

    if (resource.getStatus() != null && resource.getStatus().getValue() != latestValue) {
      errorPresent = true;
      throw new IllegalStateException(
          "status is not up to date. Latest value: "
              + latestValue
              + " status values: "
              + resource.getStatus().getValue());
    }

    var freshCopy = createFreshCopy(resource);

    latestValue = resource.getStatus() == null ? 1 : resource.getStatus().getValue() + 1;
    freshCopy.getStatus().setValue(latestValue);

    return UpdateControl.patchStatus(freshCopy);
  }

  @Override
  public List<EventSource<?, UpdateControlPrimaryCacheCustomResource>> prepareEventSources(
      EventSourceContext<UpdateControlPrimaryCacheCustomResource> context) {
    // periodic event triggering for testing purposes
    return List.of(new PeriodicTriggerEventSource<>(context.getPrimaryCache()));
  }

  private UpdateControlPrimaryCacheCustomResource createFreshCopy(
      UpdateControlPrimaryCacheCustomResource resource) {
    var res = new UpdateControlPrimaryCacheCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setStatus(new UpdateControlPrimaryCacheStatus());

    return res;
  }
}
