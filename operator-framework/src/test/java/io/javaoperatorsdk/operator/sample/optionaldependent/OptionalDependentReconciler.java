package io.javaoperatorsdk.operator.sample.optionaldependent;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {
    @Dependent(type = OptionalDependent.class, optional = true),
})
@ControllerConfiguration(
    generationAwareEventProcessing = false, // to easily trigger reconciliation with metadata update
    namespaces = Constants.WATCH_CURRENT_NAMESPACE)
public class OptionalDependentReconciler
    implements Reconciler<OptionalDependentCustomResource> {

  @Override
  public UpdateControl<OptionalDependentCustomResource> reconcile(
      OptionalDependentCustomResource resource,
      Context<OptionalDependentCustomResource> context) {
    return UpdateControl.noUpdate();
  }


}
