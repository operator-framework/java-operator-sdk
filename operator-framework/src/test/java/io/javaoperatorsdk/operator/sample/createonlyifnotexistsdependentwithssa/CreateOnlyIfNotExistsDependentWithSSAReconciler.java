package io.javaoperatorsdk.operator.sample.createonlyifnotexistsdependentwithssa;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(dependents = {
    @Dependent(type = ConfigMapDependentResource.class)})
public class CreateOnlyIfNotExistsDependentWithSSAReconciler
    implements Reconciler<CreateOnlyIfNotExistsDependentWithSSACustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CreateOnlyIfNotExistsDependentWithSSACustomResource> reconcile(
      CreateOnlyIfNotExistsDependentWithSSACustomResource resource,
      Context<CreateOnlyIfNotExistsDependentWithSSACustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }



}
