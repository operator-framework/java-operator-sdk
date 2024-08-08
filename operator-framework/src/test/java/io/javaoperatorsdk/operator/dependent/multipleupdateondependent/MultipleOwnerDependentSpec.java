package io.javaoperatorsdk.operator.dependent.multipleupdateondependent;

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
