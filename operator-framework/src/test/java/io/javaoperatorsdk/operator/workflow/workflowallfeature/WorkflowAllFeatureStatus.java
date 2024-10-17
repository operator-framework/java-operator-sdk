package io.javaoperatorsdk.operator.workflow.workflowallfeature;

public class WorkflowAllFeatureStatus {

  private Boolean ready;
  private String msgFromCondition;

  public Boolean getReady() {
    return ready;
  }

  public String getMsgFromCondition() {
    return msgFromCondition;
  }

  public WorkflowAllFeatureStatus withReady(Boolean ready) {
    this.ready = ready;
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  public WorkflowAllFeatureStatus withMsgFromCondition(String msgFromCondition) {
    this.msgFromCondition = msgFromCondition;
    return this;
  }
}
