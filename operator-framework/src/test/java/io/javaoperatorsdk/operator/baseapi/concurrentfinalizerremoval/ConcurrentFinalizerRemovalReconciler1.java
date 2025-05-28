package io.javaoperatorsdk.operator.baseapi.concurrentfinalizerremoval;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class ConcurrentFinalizerRemovalReconciler1
    implements Reconciler<ConcurrentFinalizerRemovalCustomResource>,
        Cleaner<ConcurrentFinalizerRemovalCustomResource> {

  @Override
  public UpdateControl<ConcurrentFinalizerRemovalCustomResource> reconcile(
      ConcurrentFinalizerRemovalCustomResource resource,
      Context<ConcurrentFinalizerRemovalCustomResource> context) {
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      ConcurrentFinalizerRemovalCustomResource resource,
      Context<ConcurrentFinalizerRemovalCustomResource> context)
      throws Exception {
    return DeleteControl.defaultDelete();
  }
}
