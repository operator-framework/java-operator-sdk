package io.javaoperatorsdk.operator.sample.statefulsetdesiredsanitizer;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    dependents = {@Dependent(type = StatefulSetDesiredSanitizerDependentResource.class)})
public class StatefulSetDesiredSanitizerReconciler
    implements Reconciler<StatefulSetDesiredSanitizerCustomResource> {

  @Override
  public UpdateControl<StatefulSetDesiredSanitizerCustomResource> reconcile(
      StatefulSetDesiredSanitizerCustomResource resource,
      Context<StatefulSetDesiredSanitizerCustomResource> context) throws Exception {
    return UpdateControl.noUpdate();
  }
}
