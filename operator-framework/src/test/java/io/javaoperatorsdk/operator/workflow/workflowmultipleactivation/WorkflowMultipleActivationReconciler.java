package io.javaoperatorsdk.operator.workflow.workflowmultipleactivation;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(
          type = ConfigMapDependentResource.class,
          activationCondition = ActivationCondition.class),
      @Dependent(type = SecretDependentResource.class)
    })
@ControllerConfiguration
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
