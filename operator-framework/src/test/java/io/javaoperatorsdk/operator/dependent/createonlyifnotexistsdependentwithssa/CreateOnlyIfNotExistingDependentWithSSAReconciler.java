package io.javaoperatorsdk.operator.dependent.createonlyifnotexistsdependentwithssa;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
@ControllerConfiguration()
public class CreateOnlyIfNotExistingDependentWithSSAReconciler
    implements Reconciler<CreateOnlyIfNotExistingDependentWithSSACustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CreateOnlyIfNotExistingDependentWithSSACustomResource> reconcile(
      CreateOnlyIfNotExistingDependentWithSSACustomResource resource,
      Context<CreateOnlyIfNotExistingDependentWithSSACustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
