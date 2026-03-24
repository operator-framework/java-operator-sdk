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
package io.javaoperatorsdk.operator.workflow.bulkactivationcondition;

import java.util.concurrent.atomic.AtomicReference;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

/**
 * Workflow:
 *
 * <pre>
 * ConfigMapDependentResource  (reconcilePrecondition = AlwaysFailingPrecondition)
 *   └── SecretBulkDependentResource  (activationCondition = AlwaysTrueActivation)
 * </pre>
 *
 * <p>On first reconciliation: ConfigMap precondition fails → markDependentsForDelete cascades to
 * SecretBulkDependentResource → NodeDeleteExecutor fires for Secret before its event source is ever
 * registered → NoEventSourceForClassException.
 */
@Workflow(
    dependents = {
      @Dependent(
          name = "configmap",
          type = ConfigMapDependentResource.class,
          reconcilePrecondition = AlwaysFailingPrecondition.class),
      @Dependent(
          type = SecretBulkDependentResource.class,
          activationCondition = AlwaysTrueActivation.class,
          dependsOn = "configmap")
    })
@ControllerConfiguration
public class BulkActivationConditionReconciler
    implements Reconciler<BulkActivationConditionCustomResource> {

  static final AtomicReference<Exception> lastError = new AtomicReference<>();

  @Override
  public UpdateControl<BulkActivationConditionCustomResource> reconcile(
      BulkActivationConditionCustomResource primary,
      Context<BulkActivationConditionCustomResource> context) {
    return UpdateControl.noUpdate();
  }

  @Override
  public ErrorStatusUpdateControl<BulkActivationConditionCustomResource> updateErrorStatus(
      BulkActivationConditionCustomResource primary,
      Context<BulkActivationConditionCustomResource> context,
      Exception e) {
    lastError.set(e);
    return ErrorStatusUpdateControl.noStatusUpdate();
  }
}
