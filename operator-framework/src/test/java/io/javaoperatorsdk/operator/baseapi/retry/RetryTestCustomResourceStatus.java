package io.javaoperatorsdk.operator.baseapi.retry;

public class RetryTestCustomResourceStatus {

  private State state;

  public State getState() {
    return state;
  }

  public RetryTestCustomResourceStatus setState(State state) {
    this.state = state;
    return this;
  }

  public enum State {
    SUCCESS,
    ERROR
  }
}
