package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowReconcileResult extends WorkflowResult {
  private Boolean allDependentsReady;

  WorkflowReconcileResult(Map<DependentResource, Detail<?>> results) {
    super(results);
  }

  public List<DependentResource> getReconciledDependents() {
    return listFilteredBy(detail -> detail.reconcileResult() != null);
  }

  public List<DependentResource> getNotReadyDependents() {
    return listFilteredBy(detail -> !detail.isConditionWithTypeMet(Condition.Type.READY));
  }

  public <T> Optional<T> getNotReadyDependentResult(DependentResource dependentResource,
      Class<T> expectedResultType) {
    return getDependentConditionResult(dependentResource, Condition.Type.READY, expectedResultType);
  }

  public boolean allDependentResourcesReady() {
    if (allDependentsReady == null) {
      allDependentsReady = getNotReadyDependents().isEmpty();
    }
    return allDependentsReady;
  }
}
