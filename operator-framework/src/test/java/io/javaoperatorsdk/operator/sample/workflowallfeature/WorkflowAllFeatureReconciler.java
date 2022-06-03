package io.javaoperatorsdk.operator.sample.workflowallfeature;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

import static io.javaoperatorsdk.operator.sample.workflowallfeature.WorkflowAllFeatureReconciler.DEPLOYMENT_NAME;

@ControllerConfiguration(dependents = {
    @Dependent(name = DEPLOYMENT_NAME, type = DeploymentDependentResource.class,
        readyCondition = DeploymentReadyCondition.class),
    @Dependent(type = ConfigMapDependentResource.class,
        reconcileCondition = ConfigMapReconcileCondition.class,
        deletePostCondition = ConfigMapDeletePostCondition.class,
        dependsOn = DEPLOYMENT_NAME)
})
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
            context.managedDependentResourceContext()
                .getWorkflowReconcileResult().orElseThrow()
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
