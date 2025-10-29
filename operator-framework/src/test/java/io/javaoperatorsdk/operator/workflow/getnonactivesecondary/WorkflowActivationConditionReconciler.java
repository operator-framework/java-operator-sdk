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
package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(type = ConfigMapDependentResource.class),
      @Dependent(
          type = RouteDependentResource.class,
          activationCondition = FalseActivationCondition.class)
    })
@ControllerConfiguration
public class WorkflowActivationConditionReconciler
    implements Reconciler<GetNonActiveSecondaryCustomResource> {

  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);

  @Override
  public UpdateControl<GetNonActiveSecondaryCustomResource> reconcile(
      GetNonActiveSecondaryCustomResource resource,
      Context<GetNonActiveSecondaryCustomResource> context) {

    // should not throw an exception even if the condition is false
    var route = context.getSecondaryResource(Route.class);

    numberOfReconciliationExecution.incrementAndGet();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }
}
