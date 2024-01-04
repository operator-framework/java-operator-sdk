package io.javaoperatorsdk.operator.sample.manageddependentdefaultdeletecondition;

import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration()
public class ManagedDependentDefaultDeleteConditionReconciler
    implements Reconciler<ManagedDependentDefaultDeleteConditionCustomResource> {

  @Override
  public UpdateControl<ManagedDependentDefaultDeleteConditionCustomResource> reconcile(
      ManagedDependentDefaultDeleteConditionCustomResource resource,
      Context<ManagedDependentDefaultDeleteConditionCustomResource> context) {

    return UpdateControl.noUpdate();
  }

}
