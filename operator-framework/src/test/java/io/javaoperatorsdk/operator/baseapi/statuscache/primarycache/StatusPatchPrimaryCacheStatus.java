package io.javaoperatorsdk.operator.baseapi.statuscache.primarycache;

public class StatusPatchPrimaryCacheStatus {

  private Integer value = 0;

  public Integer getValue() {
    return value;
  }

  public StatusPatchPrimaryCacheStatus setValue(Integer value) {
    this.value = value;
    return this;
  }
}
