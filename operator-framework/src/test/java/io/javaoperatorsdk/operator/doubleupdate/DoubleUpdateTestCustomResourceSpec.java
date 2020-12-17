package io.javaoperatorsdk.operator.doubleupdate;

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
