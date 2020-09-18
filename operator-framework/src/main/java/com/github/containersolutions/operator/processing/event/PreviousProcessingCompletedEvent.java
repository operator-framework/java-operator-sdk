package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.AbstractEvent;

public class PreviousProcessingCompletedEvent extends AbstractEvent {

    public PreviousProcessingCompletedEvent(String relatedCustomResourceUid) {
        super(relatedCustomResourceUid);
    }

}
