package io.javaoperatorsdk.operator.sample.observedgeneration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class ObservedGenerationTestReconciler
    implements Reconciler<ObservedGenerationTestCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(ObservedGenerationTestReconciler.class);

  @Override
  public UpdateControl<ObservedGenerationTestCustomResource> reconcile(
      ObservedGenerationTestCustomResource resource,
      Context<ObservedGenerationTestCustomResource> context) {
    log.info("Reconcile ObservedGenerationTestCustomResource: {}",
        resource.getMetadata().getName());
    if (resource.getStatus() == null) {
      resource.setStatus(new ObservedGenerationTestCustomResourceStatus());
    }
    return UpdateControl.patchStatus(resource);
  }
}
