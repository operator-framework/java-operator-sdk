package io.javaoperatorsdk.operator.sample.workflowallfeature;

public class WorkflowAllFeatureSpec {

  private String inputData;
  private boolean secretApproved = false;
  private boolean createConfigMap = false;


  public String getInputData() {
    return inputData;
  }

  public void setInputData(String inputData) {
    this.inputData = inputData;
  }


}
