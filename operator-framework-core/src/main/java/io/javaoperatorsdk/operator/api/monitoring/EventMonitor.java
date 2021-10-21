package io.javaoperatorsdk.operator.api.monitoring;

import io.javaoperatorsdk.operator.processing.event.Event;

public interface EventMonitor {

  EventMonitor NOOP = new EventMonitor() {
    @Override
    public void processedEvent(Event event) {}

    @Override
    public void failedEvent(Event event) {}
  };

  void processedEvent(Event event);

  void failedEvent(Event event);
}
