package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

@SuppressWarnings("rawtypes")
public class WorkflowReconcileResult extends WorkflowResult {

  private final List<DependentResource> reconciledDependents;
  private final List<DependentResource> notReadyDependents;
  private final Map<DependentResource, ReconcileResult> reconcileResults;

  public WorkflowReconcileResult(List<DependentResource> reconciledDependents,
      List<DependentResource> notReadyDependents,
      Map<DependentResource, Exception> erroredDependents,
      Map<DependentResource, ReconcileResult> reconcileResults) {
    super(erroredDependents);
    this.reconciledDependents = reconciledDependents;
    this.notReadyDependents = notReadyDependents;
    this.reconcileResults = reconcileResults;
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

  public boolean allDependentResourcesReady() {
    return notReadyDependents.isEmpty();
  }
}
