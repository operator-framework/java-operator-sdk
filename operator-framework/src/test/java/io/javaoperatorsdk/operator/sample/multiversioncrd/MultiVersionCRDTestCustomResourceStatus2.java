package io.javaoperatorsdk.operator.sample.multiversioncrd;

import java.util.ArrayList;
import java.util.List;

public class MultiVersionCRDTestCustomResourceStatus2 {

  private int value1;

  private List<String> reconciledBy = new ArrayList<>();

  public int getValue1() {
    return value1;
  }

  public MultiVersionCRDTestCustomResourceStatus2 setValue1(int value1) {
    this.value1 = value1;
    return this;
  }

  public List<String> getReconciledBy() {
    return reconciledBy;
  }

  public MultiVersionCRDTestCustomResourceStatus2 setReconciledBy(List<String> reconciledBy) {
    this.reconciledBy = reconciledBy;
    return this;
  }
}
