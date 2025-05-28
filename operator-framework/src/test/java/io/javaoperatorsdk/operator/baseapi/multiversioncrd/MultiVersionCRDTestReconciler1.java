package io.javaoperatorsdk.operator.baseapi.multiversioncrd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(informer = @Informer(labelSelector = "!version"))
public class MultiVersionCRDTestReconciler1
    implements Reconciler<MultiVersionCRDTestCustomResource1> {

  private static final Logger log = LoggerFactory.getLogger(MultiVersionCRDTestReconciler1.class);

  @Override
  public UpdateControl<MultiVersionCRDTestCustomResource1> reconcile(
      MultiVersionCRDTestCustomResource1 resource,
      Context<MultiVersionCRDTestCustomResource1> context) {
    log.info("Reconcile MultiVersionCRDTestCustomResource1: {}", resource.getMetadata().getName());
    if (resource.getStatus() == null) {
      resource.setStatus(new MultiVersionCRDTestCustomResourceStatus1());
    }
    resource.getStatus().setValue1(resource.getStatus().getValue1() + 1);
    if (!resource.getStatus().getReconciledBy().contains(getClass().getSimpleName())) {
      resource.getStatus().getReconciledBy().add(getClass().getSimpleName());
    }
    return UpdateControl.patchStatus(resource);
  }
}
