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
