package io.javaoperatorsdk.operator.sample.dependentoperationeventfiltering;

public class DependentOperationEventFilterCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public DependentOperationEventFilterCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
