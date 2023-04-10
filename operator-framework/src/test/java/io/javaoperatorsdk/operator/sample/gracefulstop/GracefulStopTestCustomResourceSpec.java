package io.javaoperatorsdk.operator.sample.gracefulstop;

public class GracefulStopTestCustomResourceSpec {

  private int value;

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }
}
