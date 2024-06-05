package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

@SuppressWarnings("rawtypes")
public class WorkflowReconcileResult extends WorkflowResult {
  private final Map<DependentResource, Object> notReadyDependents;
  private final Map<DependentResource, ReconcileResult> reconcileResults;

  public WorkflowReconcileResult(
      Map<DependentResource, Object> notReadyDependents,
      Map<DependentResource, Exception> erroredDependents,
      Map<DependentResource, ReconcileResult> reconcileResults) {
    super(erroredDependents);
    this.notReadyDependents = notReadyDependents;
    this.reconcileResults = reconcileResults;
  }

  public List<DependentResource> getReconciledDependents() {
    return reconcileResults.keySet().stream().toList();
  }

  public List<DependentResource> getNotReadyDependents() {
    return notReadyDependents.keySet().stream().toList();
  }

  @SuppressWarnings("unused")
  public Map<DependentResource, Object> getNotReadyDependentsWithDetails() {
    return notReadyDependents;
  }

  public <T> T getNotReadyDependentResult(DependentResource dependentResource,
      Class<T> expectedResultType) {
    final var result = new Object[1];
    try {
      return Optional.ofNullable(notReadyDependents.get(dependentResource))
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

  @SuppressWarnings("unused")
  public Map<DependentResource, ReconcileResult> getReconcileResults() {
    return reconcileResults;
  }

  public boolean allDependentResourcesReady() {
    return notReadyDependents.isEmpty();
  }
}
