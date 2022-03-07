package io.javaoperatorsdk.operator.sample.conditionchecker;

public class ConditionCheckerTestCustomResourceSpec {

  private int replicaCount;

  public ConditionCheckerTestCustomResourceSpec(int replicaCount) {
    this.replicaCount = replicaCount;
  }

  public ConditionCheckerTestCustomResourceSpec() {
    this(1);
  }

  public int getReplicaCount() {
    return replicaCount;
  }

  public ConditionCheckerTestCustomResourceSpec setReplicaCount(int replicaCount) {
    this.replicaCount = replicaCount;
    return this;
  }


}
