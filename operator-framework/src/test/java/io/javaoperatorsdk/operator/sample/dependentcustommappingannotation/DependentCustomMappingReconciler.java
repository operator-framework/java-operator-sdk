package io.javaoperatorsdk.operator.sample.dependentcustommappingannotation;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    dependents = {@Dependent(type = CustomMappingConfigMapDependentResource.class)})
public class DependentCustomMappingReconciler
    implements Reconciler<DependentCustomMappingCustomResource> {

  @Override
  public UpdateControl<DependentCustomMappingCustomResource> reconcile(
      DependentCustomMappingCustomResource resource,
      Context<DependentCustomMappingCustomResource> context) throws Exception {

    return UpdateControl.noUpdate();
  }


}
