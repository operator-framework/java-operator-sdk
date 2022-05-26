package io.javaoperatorsdk.operator.sample.cleanupconflict;

public class CleanupConflictCustomResourceStatus {

  private Integer value = 0;

  public Integer getValue() {
    return value;
  }

  public CleanupConflictCustomResourceStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
