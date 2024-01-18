package io.javaoperatorsdk.operator.sample.workflowmultipleactivation;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.workflow.Workflow;

@ControllerConfiguration(workflow = @Workflow(dependents = {
    @Dependent(type = ConfigMapDependentResource.class,
        activationCondition = ActivationCondition.class),
    @Dependent(type = SecretDependentResource.class)
}))
public class WorkflowMultipleActivationReconciler
    implements Reconciler<WorkflowMultipleActivationCustomResource> {

  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);

  @Override
  public UpdateControl<WorkflowMultipleActivationCustomResource> reconcile(
      WorkflowMultipleActivationCustomResource resource,
      Context<WorkflowMultipleActivationCustomResource> context) {

    numberOfReconciliationExecution.incrementAndGet();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }
}
