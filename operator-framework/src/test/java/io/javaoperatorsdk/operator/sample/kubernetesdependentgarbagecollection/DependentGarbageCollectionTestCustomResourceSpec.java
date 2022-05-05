package io.javaoperatorsdk.operator.sample.kubernetesdependentgarbagecollection;

public class DependentGarbageCollectionTestCustomResourceSpec {

  private boolean createConfigMap;

  public boolean isCreateConfigMap() {
    return createConfigMap;
  }

  public DependentGarbageCollectionTestCustomResourceSpec setCreateConfigMap(
      boolean createConfigMap) {
    this.createConfigMap = createConfigMap;
    return this;
  }
}
