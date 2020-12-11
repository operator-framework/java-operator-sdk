package io.javaoperatorsdk.operator.api;

public class RetryInfo {

  private int attemptIndex;
  private boolean lastAttempt;

  public RetryInfo(int retryNumber, boolean lastAttempt) {
    this.attemptIndex = retryNumber;
    this.lastAttempt = lastAttempt;
  }

  public int getAttemptIndex() {
    return attemptIndex;
  }

  public boolean isLastAttempt() {
    return lastAttempt;
  }

  @Override
  public String toString() {
    return "RetryInfo{" + "attemptIndex=" + attemptIndex + ", lastAttempt=" + lastAttempt + '}';
  }
}
