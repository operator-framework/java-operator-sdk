package io.javaoperatorsdk.operator.sample.customfilter;

public class CustomFilteringTestResourceSpec {

  private boolean filter1;

  private boolean filter2;

  public boolean isFilter1() {
    return filter1;
  }

  public CustomFilteringTestResourceSpec setFilter1(boolean filter1) {
    this.filter1 = filter1;
    return this;
  }

  public boolean isFilter2() {
    return filter2;
  }

  public CustomFilteringTestResourceSpec setFilter2(boolean filter2) {
    this.filter2 = filter2;
    return this;
  }
}
