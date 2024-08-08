package io.javaoperatorsdk.operator.baseapi.gracefulstop;

public class GracefulStopTestCustomResourceStatus {

  private long observedGeneration;

  public long getObservedGeneration() {
    return observedGeneration;
  }

  public void setObservedGeneration(long observedGeneration) {
    this.observedGeneration = observedGeneration;
  }
}
