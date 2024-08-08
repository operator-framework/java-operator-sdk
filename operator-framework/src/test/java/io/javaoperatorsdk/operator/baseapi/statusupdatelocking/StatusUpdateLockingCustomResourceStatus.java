package io.javaoperatorsdk.operator.baseapi.statusupdatelocking;

public class StatusUpdateLockingCustomResourceStatus {

  private Integer value = 0;

  public Integer getValue() {
    return value;
  }

  public StatusUpdateLockingCustomResourceStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
