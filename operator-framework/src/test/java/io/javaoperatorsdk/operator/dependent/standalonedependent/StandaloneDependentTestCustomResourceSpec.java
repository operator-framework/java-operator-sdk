package io.javaoperatorsdk.operator.dependent.standalonedependent;

public class StandaloneDependentTestCustomResourceSpec {

  private int replicaCount;

  public StandaloneDependentTestCustomResourceSpec(int replicaCount) {
    this.replicaCount = replicaCount;
  }

  public StandaloneDependentTestCustomResourceSpec() {
    this(1);
  }

  public int getReplicaCount() {
    return replicaCount;
  }

  public StandaloneDependentTestCustomResourceSpec setReplicaCount(int replicaCount) {
    this.replicaCount = replicaCount;
    return this;
  }
}
