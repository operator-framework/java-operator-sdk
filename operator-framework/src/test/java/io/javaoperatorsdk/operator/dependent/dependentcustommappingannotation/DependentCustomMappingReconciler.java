package io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = CustomMappingConfigMapDependentResource.class)})
@ControllerConfiguration
public class DependentCustomMappingReconciler
    implements Reconciler<DependentCustomMappingCustomResource> {

  @Override
  public UpdateControl<DependentCustomMappingCustomResource> reconcile(
      DependentCustomMappingCustomResource resource,
      Context<DependentCustomMappingCustomResource> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }
}
