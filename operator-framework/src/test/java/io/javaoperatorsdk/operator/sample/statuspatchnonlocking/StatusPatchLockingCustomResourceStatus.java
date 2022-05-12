package io.javaoperatorsdk.operator.sample.statuspatchnonlocking;

public class StatusPatchLockingCustomResourceStatus {

  private Integer value = 0;

  public Integer getValue() {
    return value;
  }

  public StatusPatchLockingCustomResourceStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
