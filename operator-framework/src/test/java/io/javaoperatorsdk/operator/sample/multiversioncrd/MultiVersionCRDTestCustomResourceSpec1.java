package io.javaoperatorsdk.operator.sample.multiversioncrd;

public class MultiVersionCRDTestCustomResourceSpec1 {

  private int intValue;

  public int getIntValue() {
    return intValue;
  }

  public MultiVersionCRDTestCustomResourceSpec1 setIntValue(int intValue) {
    this.intValue = intValue;
    return this;
  }

}
