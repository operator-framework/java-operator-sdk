package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowCleanupResult extends WorkflowResult {
  private Boolean allPostConditionsMet;

  WorkflowCleanupResult(Map<DependentResource, Detail<?>> results) {
    super(results);
  }

  public List<DependentResource> getDeleteCalledOnDependents() {
    return listFilteredBy(Detail::deleted);
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
