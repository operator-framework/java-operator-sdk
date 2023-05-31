package io.javaoperatorsdk.operator.sample.dependentdifferentnamespace;

public class DependentDifferentNamespaceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public DependentDifferentNamespaceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
