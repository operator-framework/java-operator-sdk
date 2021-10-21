package io.javaoperatorsdk.operator.api.monitoring;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public interface EventMonitor {

  EventMonitor NOOP = new EventMonitor() {
    @Override
    public void processedEvent(CustomResourceID uid, Event event) {}

    @Override
    public void failedEvent(CustomResourceID uid, Event event) {}
  };

  void processedEvent(CustomResourceID uid, Event event);

  void failedEvent(CustomResourceID uid, Event event);
}
