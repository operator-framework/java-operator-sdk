package io.javaoperatorsdk.operator.sample.workflowallfeature;

import static io.javaoperatorsdk.operator.sample.workflowallfeature.WorkflowAllFeatureReconciler.DEPLOYMENT_NAME;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = {
    @Dependent(name = DEPLOYMENT_NAME, type = DeploymentDependentResource.class,
        readyPostcondition = DeploymentReadyCondition.class),
    @Dependent(type = ConfigMapDependentResource.class,
        reconcilePrecondition = ConfigMapReconcileCondition.class,
        deletePostcondition = ConfigMapDeletePostCondition.class,
        dependsOn = DEPLOYMENT_NAME)
})
@ControllerConfiguration
public class WorkflowAllFeatureReconciler
    implements Reconciler<WorkflowAllFeatureCustomResource>,
    Cleaner<WorkflowAllFeatureCustomResource> {

  public static final String DEPLOYMENT_NAME = "deployment";

  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);
  private final AtomicInteger numberOfCleanupExecution = new AtomicInteger(0);

  @Override
  public UpdateControl<WorkflowAllFeatureCustomResource> reconcile(
      WorkflowAllFeatureCustomResource resource,
      Context<WorkflowAllFeatureCustomResource> context) {
    numberOfReconciliationExecution.addAndGet(1);
    if (resource.getStatus() == null) {
      resource.setStatus(new WorkflowAllFeatureStatus());
    }
    resource.getStatus()
        .setReady(
            context.managedWorkflowAndDependentResourceContext()
                .getWorkflowReconcileResult()
                .allDependentResourcesReady());
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }

  public int getNumberOfCleanupExecution() {
    return numberOfCleanupExecution.get();
  }

  @Override
  public DeleteControl cleanup(WorkflowAllFeatureCustomResource resource,
      Context<WorkflowAllFeatureCustomResource> context) {
    numberOfCleanupExecution.addAndGet(1);
    return DeleteControl.defaultDelete();
  }
}
