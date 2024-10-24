package io.javaoperatorsdk.operator.dependent.kubernetesdependentgarbagecollection;

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
