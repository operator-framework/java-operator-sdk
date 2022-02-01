package io.javaoperatorsdk.operator.sample.multiversioncrd;

public class MultiVersionCRDTestCustomResourceSpec1 {

  private int value;

  public int getValue() {
    return value;
  }

  public MultiVersionCRDTestCustomResourceSpec1 setValue(int value) {
    this.value = value;
    return this;
  }

}
