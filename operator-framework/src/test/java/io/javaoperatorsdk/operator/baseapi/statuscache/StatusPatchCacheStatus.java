package io.javaoperatorsdk.operator.baseapi.statuscache;

public class StatusPatchCacheStatus {

  private Integer value = 0;

  public Integer getValue() {
    return value;
  }

  public StatusPatchCacheStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
