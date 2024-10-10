package io.javaoperatorsdk.operator.dependent.servicestrictmatcher;

public class ServiceStrictMatcherSpec {

  private int value;

  public int getValue() {
    return value;
  }

  public ServiceStrictMatcherSpec setValue(int value) {
    this.value = value;
    return this;
  }
}
