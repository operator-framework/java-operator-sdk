package io.javaoperatorsdk.operator.sample.changenamespace;

public class ChangeNamespaceTestCustomResourceStatus {

  private int numberOfStatusUpdates = 0;

  public int getNumberOfStatusUpdates() {
    return numberOfStatusUpdates;
  }

  public ChangeNamespaceTestCustomResourceStatus setNumberOfStatusUpdates(
      int numberOfStatusUpdates) {
    this.numberOfStatusUpdates = numberOfStatusUpdates;
    return this;
  }
}
