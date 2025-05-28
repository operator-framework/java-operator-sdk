package io.javaoperatorsdk.operator.baseapi.manualobservedgeneration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration
public class ManualObservedGenerationReconciler
    implements Reconciler<ManualObservedGenerationCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ManualObservedGenerationCustomResource> reconcile(
      ManualObservedGenerationCustomResource resource,
      Context<ManualObservedGenerationCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    var resourceForStatusPatch = resourceForStatusPatch(resource);
    if (!Objects.equals(
        resource.getMetadata().getGeneration(),
        resourceForStatusPatch.getStatus().getObservedGeneration())) {
      resourceForStatusPatch
          .getStatus()
          .setObservedGeneration(resource.getMetadata().getGeneration());
      return UpdateControl.patchStatus(resourceForStatusPatch);
    } else {
      return UpdateControl.noUpdate();
    }
  }

  private ManualObservedGenerationCustomResource resourceForStatusPatch(
      ManualObservedGenerationCustomResource original) {
    var res = new ManualObservedGenerationCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(original.getMetadata().getName())
            .withNamespace(original.getMetadata().getNamespace())
            .build());
    res.setStatus(original.getStatus());
    if (res.getStatus() == null) {
      res.setStatus(new ManualObservedGenerationStatus());
    }
    return res;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
