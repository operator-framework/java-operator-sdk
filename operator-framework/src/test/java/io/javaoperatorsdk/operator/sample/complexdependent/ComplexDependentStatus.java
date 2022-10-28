package io.javaoperatorsdk.operator.sample.complexdependent;


public class ComplexDependentStatus {
  private ComplexDependentReconciler.RECONCILE_STATUS status;

  public ComplexDependentReconciler.RECONCILE_STATUS getStatus() {
    return status;
  }

  public ComplexDependentStatus setStatus(ComplexDependentReconciler.RECONCILE_STATUS status) {
    this.status = status;
    return this;
  }
}
