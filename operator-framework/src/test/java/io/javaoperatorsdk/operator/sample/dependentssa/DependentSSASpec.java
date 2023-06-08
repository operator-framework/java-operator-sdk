package io.javaoperatorsdk.operator.sample.dependentssa;

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
