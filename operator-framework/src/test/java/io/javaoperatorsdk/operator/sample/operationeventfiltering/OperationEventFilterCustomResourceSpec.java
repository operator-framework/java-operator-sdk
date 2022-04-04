package io.javaoperatorsdk.operator.sample.operationeventfiltering;

public class OperationEventFilterCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public OperationEventFilterCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
