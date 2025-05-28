package io.javaoperatorsdk.operator.workflow.workflowexplicitinvocation;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(explicitInvocation = true, dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class WorkflowExplicitInvocationReconciler
    implements Reconciler<WorkflowExplicitInvocationCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private volatile boolean invokeWorkflow = false;

  @Override
  public UpdateControl<WorkflowExplicitInvocationCustomResource> reconcile(
      WorkflowExplicitInvocationCustomResource resource,
      Context<WorkflowExplicitInvocationCustomResource> context) {

    numberOfExecutions.addAndGet(1);
    if (invokeWorkflow) {
      context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow();
    }

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public void setInvokeWorkflow(boolean invokeWorkflow) {
    this.invokeWorkflow = invokeWorkflow;
  }
}
