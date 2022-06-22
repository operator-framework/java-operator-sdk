package io.javaoperatorsdk.operator.api.config.eventsource;

public class EventSourceSpec<R> {

  private final String name;

  private final Class<R> eventSourceClass;

  public EventSourceSpec(String name, Class<R> eventSourceClass) {
    this.name = name;
    this.eventSourceClass = eventSourceClass;
  }

  public Class<R> getEventSourceClass() {
    return eventSourceClass;
  }

  public String getName() {
    return name;
  }

  // todo add filters
}
