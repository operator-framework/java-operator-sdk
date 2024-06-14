package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowReconcileResult extends WorkflowResult {

  public WorkflowReconcileResult(Map<DependentResource, Detail> results) {
    super(results);
  }

  public List<DependentResource> getReconciledDependents() {
    return listFilteredBy(detail -> detail.reconcileResult() != null);
  }

  public List<DependentResource> getNotReadyDependents() {
    return listFilteredBy(detail -> !Detail.isConditionMet(detail.reconcilePostconditionResult()));
  }

  public <T> T getNotReadyDependentResult(DependentResource dependentResource,
      Class<T> expectedResultType) {
    final var result = new Object[1];
    try {
      return Optional.ofNullable(results().get(dependentResource))
          .filter(cr -> !ResultCondition.NULL.equals(cr))
          .map(r -> result[0] = r)
          .map(expectedResultType::cast)
          .orElse(null);
    } catch (Exception e) {
      throw new IllegalArgumentException("Condition result " + result[0] +
          " for Dependent " + dependentResource.name() + " doesn't match expected type "
          + expectedResultType.getSimpleName(), e);
    }
  }

  public boolean allDependentResourcesReady() {
    return getNotReadyDependents().isEmpty();
  }
}
