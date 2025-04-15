package io.javaoperatorsdk.operator.baseapi.statuscache.withlock;

public class StatusPatchCacheWithLockStatus {

  private Integer value = 0;

  public Integer getValue() {
    return value;
  }

  public StatusPatchCacheWithLockStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
