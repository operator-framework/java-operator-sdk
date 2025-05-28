package io.javaoperatorsdk.operator.workflow.complexdependent;

public class ComplexWorkflowStatus {
  private ComplexWorkflowReconciler.RECONCILE_STATUS status;

  public ComplexWorkflowReconciler.RECONCILE_STATUS getStatus() {
    return status;
  }

  public ComplexWorkflowStatus setStatus(ComplexWorkflowReconciler.RECONCILE_STATUS status) {
    this.status = status;
    return this;
  }
}
