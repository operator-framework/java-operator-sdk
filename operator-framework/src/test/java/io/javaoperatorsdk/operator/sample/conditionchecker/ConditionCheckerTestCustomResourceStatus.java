package io.javaoperatorsdk.operator.sample.conditionchecker;

public class ConditionCheckerTestCustomResourceStatus {

  private Boolean ready;

  private Boolean wasNotReadyYet = false;

  public Boolean getWasNotReadyYet() {
    return wasNotReadyYet;
  }

  public void setWasNotReadyYet(Boolean wasNotReadyYet) {
    this.wasNotReadyYet = wasNotReadyYet;
  }

  public Boolean getReady() {
    return ready;
  }

  public void setReady(Boolean ready) {
    this.ready = ready;
  }
}
