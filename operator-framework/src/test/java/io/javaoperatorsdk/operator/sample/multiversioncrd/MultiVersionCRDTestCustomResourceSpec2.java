package io.javaoperatorsdk.operator.sample.multiversioncrd;

public class MultiVersionCRDTestCustomResourceSpec2 {

  private String stringValue;

  public String getStringValue() {
    return stringValue;
  }

  public MultiVersionCRDTestCustomResourceSpec2 setStringValue(String stringValue) {
    this.stringValue = stringValue;
    return this;
  }
}
