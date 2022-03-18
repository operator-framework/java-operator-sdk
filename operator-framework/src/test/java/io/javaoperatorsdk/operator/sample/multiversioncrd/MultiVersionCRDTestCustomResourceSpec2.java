package io.javaoperatorsdk.operator.sample.multiversioncrd;

public class MultiVersionCRDTestCustomResourceSpec2 {

  private String value;

  public String getValue() {
    return value;
  }

  public MultiVersionCRDTestCustomResourceSpec2 setValue(String value) {
    this.value = value;
    return this;
  }
}
