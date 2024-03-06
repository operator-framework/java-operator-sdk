package io.javaoperatorsdk.operator.sample.optionaldependent;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    generationAwareEventProcessing = false, // to easily trigger reconciliation with metadata update
    namespaces = Constants.WATCH_CURRENT_NAMESPACE,
    dependents = {
        @Dependent(type = OptionalDependent.class, optional = true),
    })
public class OptionalDependentReconciler
    implements Reconciler<OptionalDependentCustomResource> {

  @Override
  public UpdateControl<OptionalDependentCustomResource> reconcile(
      OptionalDependentCustomResource resource,
      Context<OptionalDependentCustomResource> context) {
    return UpdateControl.noUpdate();
  }


}
