package io.javaoperatorsdk.operator.baseapi.subresource;

public class SubResourceTestCustomResourceStatus {

  private State state;

  public State getState() {
    return state;
  }

  public SubResourceTestCustomResourceStatus setState(State state) {
    this.state = state;
    return this;
  }

  public enum State {
    SUCCESS,
    ERROR
  }
}
