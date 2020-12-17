package io.javaoperatorsdk.operator.sample.doubleupdate;

public class DoubleUpdateTestCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public DoubleUpdateTestCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
