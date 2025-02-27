package io.javaoperatorsdk.operator.dependent.multipleupdateondependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = {@Dependent(type = MultipleOwnerDependentConfigMap.class)})
@ControllerConfiguration()
public class MultipleOwnerDependentReconciler
    implements Reconciler<MultipleOwnerDependentCustomResource>, TestExecutionInfoProvider {

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
