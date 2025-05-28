package io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa;

public class PatchResourceAndStatusNoSSAStatus {

  private State state;

  public State getState() {
    return state;
  }

  public PatchResourceAndStatusNoSSAStatus setState(State state) {
    this.state = state;
    return this;
  }

  public enum State {
    SUCCESS,
    ERROR
  }
}
