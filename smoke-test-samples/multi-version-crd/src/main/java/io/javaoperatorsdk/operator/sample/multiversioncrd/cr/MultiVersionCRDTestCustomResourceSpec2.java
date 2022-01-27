package io.javaoperatorsdk.operator.sample.multiversioncrd.cr;

public class MultiVersionCRDTestCustomResourceSpec2 {

  private int value1;

  public int getValue1() {
    return value1;
  }

  public MultiVersionCRDTestCustomResourceSpec2 setValue1(int value1) {
    this.value1 = value1;
    return this;
  }
}
