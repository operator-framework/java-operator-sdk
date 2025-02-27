package io.javaoperatorsdk.operator.sample.observedgeneration;

public class ObservedGenSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "TestCustomResourceSpec{" + "value='" + value + '\'' + '}';
  }
}
