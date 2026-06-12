package io.javaoperatorsdk.operator.dependent.desiredonce;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = @Dependent(type = DesiredOnceDependent.class))
public class DesiredOnceReconciler implements Reconciler<DesiredOnce> {
  @Override
  public UpdateControl<DesiredOnce> reconcile(DesiredOnce resource, Context<DesiredOnce> context)
      throws Exception {
    return UpdateControl.noUpdate();
  }
}
