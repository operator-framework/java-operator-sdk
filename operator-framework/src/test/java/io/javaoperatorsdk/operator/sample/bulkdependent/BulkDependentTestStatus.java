package io.javaoperatorsdk.operator.sample.bulkdependent;

public class BulkDependentTestStatus {

  private Boolean ready;

  public Boolean getReady() {
    return ready;
  }

  public BulkDependentTestStatus setReady(boolean ready) {
    this.ready = ready;
    return this;
  }
}
