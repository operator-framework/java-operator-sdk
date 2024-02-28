package io.javaoperatorsdk.operator.sample.multipledependentresource;

public class MultipleDependentResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public MultipleDependentResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
