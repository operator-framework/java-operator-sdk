package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.api.Event;
import io.javaoperatorsdk.operator.api.EventSource;

public abstract class AbstractEvent implements Event {
  
  private final String relatedCustomResourceUid;
  
  private final EventSource eventSource;
  
  public AbstractEvent(String relatedCustomResourceUid, EventSource eventSource) {
    this.relatedCustomResourceUid = relatedCustomResourceUid;
    this.eventSource = eventSource;
  }

  @Override
  public String getRelatedCustomResourceUid() {
    return relatedCustomResourceUid;
  }

  @Override
  public EventSource getEventSource() {
    return eventSource;
  }

  @Override
  public String toString() {
    return "{ class="
        + this.getClass().getName()
        + ", relatedCustomResourceUid="
        + relatedCustomResourceUid
        + ", eventSource="
        + eventSource
        + " }";
  }
}
