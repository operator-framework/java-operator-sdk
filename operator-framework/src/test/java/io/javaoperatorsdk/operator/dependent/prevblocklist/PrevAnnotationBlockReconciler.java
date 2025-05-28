package io.javaoperatorsdk.operator.dependent.prevblocklist;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = {@Dependent(type = DeploymentDependent.class)})
@ControllerConfiguration()
public class PrevAnnotationBlockReconciler
    implements Reconciler<PrevAnnotationBlockCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public PrevAnnotationBlockReconciler() {}

  @Override
  public UpdateControl<PrevAnnotationBlockCustomResource> reconcile(
      PrevAnnotationBlockCustomResource resource,
      Context<PrevAnnotationBlockCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
