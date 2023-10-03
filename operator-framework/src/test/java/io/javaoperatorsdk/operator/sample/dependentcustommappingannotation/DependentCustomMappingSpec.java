package io.javaoperatorsdk.operator.sample.dependentcustommappingannotation;

public class DependentCustomMappingSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public DependentCustomMappingSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
