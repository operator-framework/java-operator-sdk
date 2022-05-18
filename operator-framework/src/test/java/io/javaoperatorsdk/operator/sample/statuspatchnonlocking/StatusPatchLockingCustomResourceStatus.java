package io.javaoperatorsdk.operator.sample.statuspatchnonlocking;

public class StatusPatchLockingCustomResourceStatus {

  private Integer value = 0;

  private String message;

  public String getMessage() {
    return message;
  }

  public StatusPatchLockingCustomResourceStatus setMessage(String message) {
    this.message = message;
    return this;
  }

  public Integer getValue() {
    return value;
  }

  public StatusPatchLockingCustomResourceStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
