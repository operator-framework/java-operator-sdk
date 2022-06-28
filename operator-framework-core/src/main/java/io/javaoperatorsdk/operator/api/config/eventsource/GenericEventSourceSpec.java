package io.javaoperatorsdk.operator.api.config.eventsource;

@SuppressWarnings("rawtypes")
public class GenericEventSourceSpec extends EventSourceSpec {

  private final Class eventSourceClass;

  public GenericEventSourceSpec(String name, Class eventSourceClass) {
    super(name);
    this.eventSourceClass = eventSourceClass;
  }

  public Class getEventSourceClass() {
    return eventSourceClass;
  }
}
