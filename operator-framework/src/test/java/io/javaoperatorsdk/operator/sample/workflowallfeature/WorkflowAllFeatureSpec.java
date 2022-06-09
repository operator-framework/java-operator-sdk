package io.javaoperatorsdk.operator.sample.workflowallfeature;

public class WorkflowAllFeatureSpec {

  private boolean createConfigMap = false;

  public boolean isCreateConfigMap() {
    return createConfigMap;
  }

  public WorkflowAllFeatureSpec setCreateConfigMap(boolean createConfigMap) {
    this.createConfigMap = createConfigMap;
    return this;
  }
}
