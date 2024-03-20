package io.javaoperatorsdk.operator.processing.event.source.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;

public class SimpleInboundEventSource extends AbstractEventSource {

  private static final Logger log = LoggerFactory.getLogger(SimpleInboundEventSource.class);

  public void propagateEvent(ResourceID resourceID) {
    if (isRunning()) {
      getEventHandler().handleEvent(new Event(resourceID));
    } else {
      log.debug("Event source not started yet, not propagating event for: {}", resourceID);
    }
  }

}
