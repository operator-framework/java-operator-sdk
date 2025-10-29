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
