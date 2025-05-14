package io.javaoperatorsdk.operator.baseapi.statuscache.internalwithlock;

public class StatusPatchCacheWithLockSpec {

  private int counter = 0;

  public int getCounter() {
    return counter;
  }

  public void setCounter(int counter) {
    this.counter = counter;
  }
}
