package io.javaoperatorsdk.operator.sample.patchresourcewithssa;

public class PatchResourceWithSSAStatus {

  private boolean successfullyReconciled;

  public boolean isSuccessfullyReconciled() {
    return successfullyReconciled;
  }

  public void setSuccessfullyReconciled(boolean successfullyReconciled) {
    this.successfullyReconciled = successfullyReconciled;
  }
}
