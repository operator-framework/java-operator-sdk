package io.javaoperatorsdk.operator.sample.doubleupdate;

public class DoubleUpdateTestCustomResourceStatus {

  private State state;

  public State getState() {
    return state;
  }

  public DoubleUpdateTestCustomResourceStatus setState(State state) {
    this.state = state;
    return this;
  }

  public enum State {
    SUCCESS, ERROR
  }
}
