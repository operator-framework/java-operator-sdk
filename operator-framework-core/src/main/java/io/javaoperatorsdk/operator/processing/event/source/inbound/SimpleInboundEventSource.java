package io.javaoperatorsdk.operator.processing.event.source.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ObjectKey;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;

public class SimpleInboundEventSource extends AbstractEventSource {

  private static final Logger log = LoggerFactory.getLogger(SimpleInboundEventSource.class);

  public void propagateEvent(ObjectKey objectKey) {
    if (isRunning()) {
      getEventHandler().handleEvent(new Event(objectKey));
    } else {
      log.debug("Event source not started yet, not propagating event for: {}", objectKey);
    }
  }

}
