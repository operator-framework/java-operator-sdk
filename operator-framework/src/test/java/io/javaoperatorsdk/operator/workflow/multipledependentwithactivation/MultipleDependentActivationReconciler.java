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
package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(
          type = ConfigMapDependentResource1.class,
          activationCondition = ActivationCondition.class),
      @Dependent(
          type = ConfigMapDependentResource2.class,
          activationCondition = ActivationCondition.class),
      @Dependent(type = SecretDependentResource.class)
    })
@ControllerConfiguration
public class MultipleDependentActivationReconciler
    implements Reconciler<MultipleDependentActivationCustomResource> {

  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);

  @Override
  public UpdateControl<MultipleDependentActivationCustomResource> reconcile(
      MultipleDependentActivationCustomResource resource,
      Context<MultipleDependentActivationCustomResource> context) {

    numberOfReconciliationExecution.incrementAndGet();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }
}
