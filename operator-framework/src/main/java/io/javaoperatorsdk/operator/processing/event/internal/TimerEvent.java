package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.AbstractEvent;

public class TimerEvent extends AbstractEvent {

    public TimerEvent(String relatedCustomResourceUid, TimerEventSource eventSource) {
        super(relatedCustomResourceUid, eventSource);
    }
}
