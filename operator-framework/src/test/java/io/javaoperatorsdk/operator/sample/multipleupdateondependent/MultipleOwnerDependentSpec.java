package io.javaoperatorsdk.operator.sample.multipleupdateondependent;

public class MultipleOwnerDependentSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public MultipleOwnerDependentSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
