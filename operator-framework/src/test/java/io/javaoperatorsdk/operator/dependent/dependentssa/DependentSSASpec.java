package io.javaoperatorsdk.operator.dependent.dependentssa;

public class DependentSSASpec {

  private String value;

  public String getValue() {
    return value;
  }

  public DependentSSASpec setValue(String value) {
    this.value = value;
    return this;
  }
}
