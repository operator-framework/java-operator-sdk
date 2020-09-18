package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.Event;

public abstract class AbstractEvent implements Event {

    private String relatedCustomResourceUid;

    public AbstractEvent(String relatedCustomResourceUid) {
        this.relatedCustomResourceUid = relatedCustomResourceUid;
    }

    @Override
    public String getRelatedCustomResourceUid() {
        return relatedCustomResourceUid;
    }
}
