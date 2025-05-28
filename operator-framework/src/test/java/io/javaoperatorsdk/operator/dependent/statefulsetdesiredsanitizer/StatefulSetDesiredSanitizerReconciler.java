package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = StatefulSetDesiredSanitizerDependentResource.class)})
@ControllerConfiguration
public class StatefulSetDesiredSanitizerReconciler
    implements Reconciler<StatefulSetDesiredSanitizerCustomResource> {

  @Override
  public UpdateControl<StatefulSetDesiredSanitizerCustomResource> reconcile(
      StatefulSetDesiredSanitizerCustomResource resource,
      Context<StatefulSetDesiredSanitizerCustomResource> context)
      throws Exception {
    return UpdateControl.noUpdate();
  }
}
