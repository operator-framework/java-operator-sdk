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
package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
class DefaultWorkflowReconcileResult extends BaseWorkflowResult implements WorkflowReconcileResult {
  DefaultWorkflowReconcileResult(Map<DependentResource, Detail<?>> results) {
    super(results);
  }

  public List<DependentResource> getReconciledDependents() {
    return listFilteredBy(detail -> detail.reconcileResult() != null);
  }

  public List<DependentResource> getNotReadyDependents() {
    return listFilteredBy(detail -> !detail.isConditionWithTypeMet(Condition.Type.READY));
  }

  public <T> Optional<T> getNotReadyDependentResult(
      DependentResource dependentResource, Class<T> expectedResultType) {
    return getDependentConditionResult(dependentResource, Condition.Type.READY, expectedResultType);
  }
}
