package io.javaoperatorsdk.operator.sample.ssastatefulsetmatcherissue;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(dependents = {@Dependent(type = StatefulSetDependentResource.class)})
public class SSAStatefulSetMatcherIssueReconciler
    implements Reconciler<SSAStatefulSetMatcherIssueCustomResource> {

  @Override
  public UpdateControl<SSAStatefulSetMatcherIssueCustomResource> reconcile(
      SSAStatefulSetMatcherIssueCustomResource resource,
      Context<SSAStatefulSetMatcherIssueCustomResource> context) throws Exception {
    return UpdateControl.noUpdate();
  }

}
