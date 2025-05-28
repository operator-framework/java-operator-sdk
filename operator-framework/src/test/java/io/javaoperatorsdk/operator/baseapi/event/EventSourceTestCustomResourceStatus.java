package io.javaoperatorsdk.operator.baseapi.event;

public class EventSourceTestCustomResourceStatus {

  private State state;

  public State getState() {
    return state;
  }

  public EventSourceTestCustomResourceStatus setState(State state) {
    this.state = state;
    return this;
  }

  public enum State {
    SUCCESS,
    ERROR
  }
}
