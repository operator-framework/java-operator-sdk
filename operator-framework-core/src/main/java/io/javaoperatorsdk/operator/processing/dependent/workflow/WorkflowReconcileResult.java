package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowReconcileResult extends WorkflowResult {

  public WorkflowReconcileResult(Map<DependentResource, Detail<?>> results) {
    super(results);
  }

  public List<DependentResource> getReconciledDependents() {
    return listFilteredBy(detail -> detail.reconcileResult() != null);
  }

  public List<DependentResource> getNotReadyDependents() {
    return listFilteredBy(detail -> !detail.isConditionWithTypeMet(Condition.Type.READY));
  }

  public <T> T getNotReadyDependentResult(DependentResource dependentResource,
      Class<T> expectedResultType) {
    final var result = new Object[1];
    try {
      return Optional.ofNullable(results().get(dependentResource))
          .flatMap(detail -> detail.getResultForConditionWithType(Condition.Type.READY))
          .map(r -> result[0] = r.getResult())
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
