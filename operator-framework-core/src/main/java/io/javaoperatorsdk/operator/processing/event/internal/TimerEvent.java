package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

public class TimerEvent extends DefaultEvent {

  public TimerEvent(CustomResourceID relatedCustomResourceUid) {
    super(relatedCustomResourceUid);
  }
}
