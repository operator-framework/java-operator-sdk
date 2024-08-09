package io.javaoperatorsdk.operator.baseapi.primarytosecondary;

public class JobSpec {

  private String clusterName;

  public String getClusterName() {
    return clusterName;
  }

  public JobSpec setClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }
}
