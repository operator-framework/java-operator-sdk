package io.javaoperatorsdk.operator.baseapi.ssaissue;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class SSAFinalizerIssueReconciler
    implements Reconciler<SSAFinalizerIssueCustomResource>,
        Cleaner<SSAFinalizerIssueCustomResource> {

  @Override
  public DeleteControl cleanup(
      SSAFinalizerIssueCustomResource resource, Context<SSAFinalizerIssueCustomResource> context) {
    return DeleteControl.defaultDelete();
  }

  @Override
  public UpdateControl<SSAFinalizerIssueCustomResource> reconcile(
      SSAFinalizerIssueCustomResource resource, Context<SSAFinalizerIssueCustomResource> context) {
    return UpdateControl.noUpdate();
  }
}
