package io.javaoperatorsdk.operator.dependent.cleanermanageddependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
@ControllerConfiguration
public class CleanerForManagedDependentTestReconciler
    implements Reconciler<CleanerForManagedDependentCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CleanerForManagedDependentCustomResource> reconcile(
      CleanerForManagedDependentCustomResource resource,
      Context<CleanerForManagedDependentCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
