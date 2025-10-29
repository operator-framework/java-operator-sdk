/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.javaoperatorsdk.operator.workflow.workflowallfeature.WorkflowAllFeatureReconciler.DEPLOYMENT_NAME;

@Workflow(
    dependents = {
      @Dependent(
          name = DEPLOYMENT_NAME,
          type = DeploymentDependentResource.class,
          readyPostcondition = DeploymentReadyCondition.class),
      @Dependent(
          type = ConfigMapDependentResource.class,
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
    final var reconcileResult =
        context.managedWorkflowAndDependentResourceContext().getWorkflowReconcileResult();
    final var msgFromCondition =
        reconcileResult
            .orElseThrow()
            .getDependentConditionResult(
                DependentResource.defaultNameFor(ConfigMapDependentResource.class),
                Condition.Type.RECONCILE,
                String.class)
            .orElse(ConfigMapReconcileCondition.NOT_RECONCILED_YET);
    resource
        .getStatus()
        .withReady(reconcileResult.orElseThrow().allDependentResourcesReady())
        .withMsgFromCondition(msgFromCondition);
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }

  public int getNumberOfCleanupExecution() {
    return numberOfCleanupExecution.get();
  }

  @Override
  public DeleteControl cleanup(
      WorkflowAllFeatureCustomResource resource,
      Context<WorkflowAllFeatureCustomResource> context) {
    numberOfCleanupExecution.addAndGet(1);
    return DeleteControl.defaultDelete();
  }
}
