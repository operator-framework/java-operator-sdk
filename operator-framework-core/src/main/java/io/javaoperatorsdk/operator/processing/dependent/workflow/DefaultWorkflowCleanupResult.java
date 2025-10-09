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

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
class DefaultWorkflowCleanupResult extends BaseWorkflowResult implements WorkflowCleanupResult {
  private Boolean allPostConditionsMet;

  DefaultWorkflowCleanupResult(Map<DependentResource, BaseWorkflowResult.Detail<?>> results) {
    super(results);
  }

  public List<DependentResource> getDeleteCalledOnDependents() {
    return listFilteredBy(BaseWorkflowResult.Detail::deleted);
  }

  public List<DependentResource> getPostConditionNotMetDependents() {
    return listFilteredBy(detail -> !detail.isConditionWithTypeMet(Condition.Type.DELETE));
  }

  public boolean allPostConditionsMet() {
    if (allPostConditionsMet == null) {
      allPostConditionsMet = getPostConditionNotMetDependents().isEmpty();
    }
    return allPostConditionsMet;
  }
}
