package io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype;

public class MultipleManagedDependentResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public MultipleManagedDependentResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
