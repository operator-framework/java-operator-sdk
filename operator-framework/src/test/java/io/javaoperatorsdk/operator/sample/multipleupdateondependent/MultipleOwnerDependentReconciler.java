package io.javaoperatorsdk.operator.sample.multipleupdateondependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(dependents = {
    @Dependent(type = MultipleOwnerDependentConfigMap.class)
})
public class MultipleOwnerDependentReconciler
    implements Reconciler<MultipleOwnerDependentCustomResource>,
    TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public MultipleOwnerDependentReconciler() {}

  @Override
  public UpdateControl<MultipleOwnerDependentCustomResource> reconcile(
      MultipleOwnerDependentCustomResource resource,
      Context<MultipleOwnerDependentCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
