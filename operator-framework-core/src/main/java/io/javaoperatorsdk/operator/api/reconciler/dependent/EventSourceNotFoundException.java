package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.OperatorException;

public class EventSourceNotFoundException extends OperatorException {

  private final String eventSourceName;

  public EventSourceNotFoundException(String eventSourceName) {
    this.eventSourceName = eventSourceName;
  }

  public String getEventSourceName() {
    return eventSourceName;
  }
}
