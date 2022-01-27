package io.javaoperatorsdk.operator.sample.multiversioncrd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.multiversioncrd.cr.MultiVersionCRDTestCustomResource1;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER, labelSelector = "version in (v1)")
public class MultiVersionCRDTestReconciler1
    implements Reconciler<MultiVersionCRDTestCustomResource1> {

  private static final Logger log = LoggerFactory.getLogger(MultiVersionCRDTestReconciler1.class);

  @Override
  public UpdateControl<MultiVersionCRDTestCustomResource1> reconcile(
      MultiVersionCRDTestCustomResource1 resource, Context context) {
    log.info("Reconcile MultiVersionCRDTestCustomResource1: {}",
        resource.getMetadata().getName());
    resource.getStatus().setValue1(resource.getStatus().getValue1() + 1);
    resource.getStatus().setValue2(resource.getStatus().getValue2() + 1);
    if (!resource.getStatus().getReconciledBy().contains(getClass().getSimpleName())) {
      resource.getStatus().getReconciledBy().add(getClass().getSimpleName());
    }
    return UpdateControl.updateStatus(resource);
  }
}
