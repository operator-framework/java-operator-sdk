package com.github.containersolutions.operator.processing.event.internal;

import com.github.containersolutions.operator.processing.event.AbstractEvent;
import com.github.containersolutions.operator.processing.event.EventSource;

public class DelayedReprocessEvent extends AbstractEvent {

    public DelayedReprocessEvent(String relatedCustomResourceUid, EventSource eventSource) {
        super(relatedCustomResourceUid, eventSource);
    }

}
