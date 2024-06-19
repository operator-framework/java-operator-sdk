package io.javaoperatorsdk.operator.sample.multipledrsametypenodiscriminator;

public class MultipleManagedDependentNoDiscriminatorSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public MultipleManagedDependentNoDiscriminatorSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
