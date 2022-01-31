package io.javaoperatorsdk.operator.sample.multiversioncrd;

public class MultiVersionCRDTestCustomResourceSpec1 {

  private int value1;

  private int value2;

  public int getValue1() {
    return value1;
  }

  public MultiVersionCRDTestCustomResourceSpec1 setValue1(int value1) {
    this.value1 = value1;
    return this;
  }

  public int getValue2() {
    return value2;
  }

  public MultiVersionCRDTestCustomResourceSpec1 setValue2(int value2) {
    this.value2 = value2;
    return this;
  }
}
