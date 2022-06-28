package io.javaoperatorsdk.operator.api.config.eventsource;

public abstract class EventSourceSpec {

  private final String name;

  public EventSourceSpec(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  // todo add filters
}
