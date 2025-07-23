package io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer;

public class SSAFinalizerIssueStatus {

  private String configMapStatus;

  public String getConfigMapStatus() {
    return configMapStatus;
  }

  public void setConfigMapStatus(String configMapStatus) {
    this.configMapStatus = configMapStatus;
  }

  @Override
  public String toString() {
    return "TestCustomResourceStatus{" + "configMapStatus='" + configMapStatus + '\'' + '}';
  }
}
