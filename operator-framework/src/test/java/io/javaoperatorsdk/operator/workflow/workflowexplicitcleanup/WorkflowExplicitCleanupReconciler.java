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
package io.javaoperatorsdk.operator.workflow.workflowexplicitcleanup;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(explicitInvocation = true, dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class WorkflowExplicitCleanupReconciler
    implements Reconciler<WorkflowExplicitCleanupCustomResource>,
        Cleaner<WorkflowExplicitCleanupCustomResource> {

  @Override
  public UpdateControl<WorkflowExplicitCleanupCustomResource> reconcile(
      WorkflowExplicitCleanupCustomResource resource,
      Context<WorkflowExplicitCleanupCustomResource> context) {

    context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow();

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      WorkflowExplicitCleanupCustomResource resource,
      Context<WorkflowExplicitCleanupCustomResource> context) {

    context.managedWorkflowAndDependentResourceContext().cleanupManageWorkflow();
    // this can be checked
    // context.managedWorkflowAndDependentResourceContext().getWorkflowCleanupResult()
    return DeleteControl.defaultDelete();
  }
}
