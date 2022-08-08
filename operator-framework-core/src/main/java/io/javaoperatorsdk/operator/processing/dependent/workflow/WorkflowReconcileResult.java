package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

@SuppressWarnings("rawtypes")
public class WorkflowReconcileResult {

  private final List<DependentResource> reconciledDependents;
  private final List<DependentResource> notReadyDependents;
  private final Map<DependentResource, Exception> erroredDependents;
  private final Map<DependentResource, ReconcileResult> reconcileResults;

  public WorkflowReconcileResult(List<DependentResource> reconciledDependents,
      List<DependentResource> notReadyDependents,
      Map<DependentResource, Exception> erroredDependents,
      Map<DependentResource, ReconcileResult> reconcileResults) {
    this.reconciledDependents = reconciledDependents;
    this.notReadyDependents = notReadyDependents;
    this.erroredDependents = erroredDependents;
    this.reconcileResults = reconcileResults;
  }

  public Map<DependentResource, Exception> getErroredDependents() {
    return erroredDependents;
  }

  public List<DependentResource> getReconciledDependents() {
    return reconciledDependents;
  }

  public List<DependentResource> getNotReadyDependents() {
    return notReadyDependents;
  }

  @SuppressWarnings("unused")
  public Map<DependentResource, ReconcileResult> getReconcileResults() {
    return reconcileResults;
  }

  public void throwAggregateExceptionIfErrorsPresent() {
    if (!erroredDependents.isEmpty()) {
      throw createFinalException();
    }
  }

  private AggregatedOperatorException createFinalException() {
    return new AggregatedOperatorException("Exception during workflow.",
        new ArrayList<>(erroredDependents.values()));
  }

  public boolean allDependentResourcesReady() {
    return notReadyDependents.isEmpty();
  }

  /**
   * @deprecated Use {@link #erroredDependentsExist()} instead
   */
  @Deprecated(forRemoval = true)
  public boolean erroredDependentsExists() {
    return !erroredDependents.isEmpty();
  }

  @SuppressWarnings("unused")
  public boolean erroredDependentsExist() {
    return !erroredDependents.isEmpty();
  }
}
