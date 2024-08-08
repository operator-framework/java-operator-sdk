package io.javaoperatorsdk.operator.dependent.dependentfilter;

public class DependentFilterTestResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public DependentFilterTestResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
