package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

public class TimerEvent extends DefaultEvent {

  public TimerEvent(String relatedCustomResourceUid, TimerEventSource eventSource) {
    super(relatedCustomResourceUid, eventSource);
  }
}
