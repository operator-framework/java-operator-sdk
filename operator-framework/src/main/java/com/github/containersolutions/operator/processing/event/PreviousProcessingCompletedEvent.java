package com.github.containersolutions.operator.processing.event;

public class PreviousProcessingCompletedEvent extends AbstractEvent {

    public PreviousProcessingCompletedEvent(String relatedCustomResourceUid) {
        super(relatedCustomResourceUid, eventSource);
    }

}
