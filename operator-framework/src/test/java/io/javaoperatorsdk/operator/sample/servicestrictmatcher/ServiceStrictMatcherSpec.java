package io.javaoperatorsdk.operator.sample.servicestrictmatcher;

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
