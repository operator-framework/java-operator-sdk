package com.github.containersolutions.operator.processing.event.internal;

import com.github.containersolutions.operator.processing.event.AbstractEvent;

public class PreviousProcessingCompletedEvent extends AbstractEvent<PreviousProcessingCompletedEventSource> {

    public PreviousProcessingCompletedEvent(String relatedCustomResourceUid,
                                            PreviousProcessingCompletedEventSource eventSource) {
        super(relatedCustomResourceUid, eventSource);
    }

}
