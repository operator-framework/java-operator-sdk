package io.javaoperatorsdk.operator.processing.event.internal;


import io.javaoperatorsdk.operator.processing.event.AbstractEvent;
import io.javaoperatorsdk.operator.processing.event.EventSource;

public class DelayedReprocessEvent extends AbstractEvent {

    public DelayedReprocessEvent(String relatedCustomResourceUid, EventSource eventSource) {
        super(relatedCustomResourceUid, eventSource);
    }

}
