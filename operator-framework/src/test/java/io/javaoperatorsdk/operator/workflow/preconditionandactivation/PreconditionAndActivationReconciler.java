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
package io.javaoperatorsdk.operator.workflow.preconditionandactivation;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(
          name = "configmap",
          type = ConfigMapDependentResource.class,
          reconcilePrecondition = FalseReconcilePrecondition.class),
      @Dependent(
          type = NotAvailableDependentResource.class,
          activationCondition = FalseActivationCondition.class,
          dependsOn = "configmap")
    })
@ControllerConfiguration
public class PreconditionAndActivationReconciler
    implements Reconciler<PreconditionAndActivationCustomResource> {

  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);

  @Override
  public UpdateControl<PreconditionAndActivationCustomResource> reconcile(
      PreconditionAndActivationCustomResource resource,
      Context<PreconditionAndActivationCustomResource> context) {

    var workflowResult =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowReconcileResult()
            .orElseThrow();
    var erroredDependents = workflowResult.getErroredDependents();
    if (!erroredDependents.isEmpty()) {
      throw new RuntimeException(
          "Unexpected workflow error", erroredDependents.values().iterator().next());
    }

    numberOfReconciliationExecution.incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }
}
