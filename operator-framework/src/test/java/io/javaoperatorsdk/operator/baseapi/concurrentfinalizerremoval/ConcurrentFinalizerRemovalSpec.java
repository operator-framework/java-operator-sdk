package io.javaoperatorsdk.operator.baseapi.concurrentfinalizerremoval;

public class ConcurrentFinalizerRemovalSpec {

  private int number;

  public int getNumber() {
    return number;
  }

  public ConcurrentFinalizerRemovalSpec setNumber(int number) {
    this.number = number;
    return this;
  }
}
