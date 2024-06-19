package io.javaoperatorsdk.operator.sample.workflowexplicitcleanup;

public class WorkflowExplicitCleanupSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public WorkflowExplicitCleanupSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
