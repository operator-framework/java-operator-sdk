package io.javaoperatorsdk.operator.baseapi.triggerallevent.finalizerhandling;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(triggerReconcilerOnAllEvent = true)
public class SelectiveFinalizerHandlingReconciler
    implements Reconciler<SelectiveFinalizerHandlingReconcilerCustomResource> {

  public static final String FINALIZER = "finalizer.test/finalizer";

  @Override
  public UpdateControl<SelectiveFinalizerHandlingReconcilerCustomResource> reconcile(
      SelectiveFinalizerHandlingReconcilerCustomResource resource,
      Context<SelectiveFinalizerHandlingReconcilerCustomResource> context) {

    if (context.isPrimaryResourceDeleted()) {
      return UpdateControl.noUpdate();
    }

    if (resource.getSpec().getUseFinalizer()) {
      PrimaryUpdateAndCacheUtils.addFinalizer(context, FINALIZER);
    }

    if (resource.isMarkedForDeletion()) {
      PrimaryUpdateAndCacheUtils.removeFinalizer(context, FINALIZER);
    }

    return UpdateControl.noUpdate();
  }
}
