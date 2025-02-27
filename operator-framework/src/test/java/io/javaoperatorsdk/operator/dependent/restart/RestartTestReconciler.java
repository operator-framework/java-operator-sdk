package io.javaoperatorsdk.operator.dependent.restart;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = @Dependent(type = ConfigMapDependentResource.class))
@ControllerConfiguration
public class RestartTestReconciler
    implements Reconciler<RestartTestCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<RestartTestCustomResource> reconcile(
      RestartTestCustomResource resource, Context<RestartTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
