package io.javaoperatorsdk.operator.sample.customfilter;

public class CustomFilteringTestResourceSpec {

  private boolean reconcile = true;

  private String value;

  public String getValue() {
    return value;
  }

  public CustomFilteringTestResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }

  public boolean isReconcile() {
    return reconcile;
  }

  public CustomFilteringTestResourceSpec setReconcile(boolean reconcile) {
    this.reconcile = reconcile;
    return this;
  }
}
