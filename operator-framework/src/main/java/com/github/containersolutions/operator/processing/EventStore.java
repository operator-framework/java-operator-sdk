package com.github.containersolutions.operator.processing;

import java.util.HashMap;
import java.util.Map;

public class EventStore {

    private final Map<String, CustomResourceEvent> eventsNotScheduled = new HashMap<>();
    private final Map<String, CustomResourceEvent> eventsUnderProcessing = new HashMap<>();

    public boolean containsNotScheduledEvent(String uuid) {
        return eventsNotScheduled.containsKey(uuid);
    }

    public CustomResourceEvent removeEventNotScheduled(String uid) {
        return eventsNotScheduled.remove(uid);
    }

    public void addOrReplaceEventAsNotScheduled(CustomResourceEvent event) {
        eventsNotScheduled.put(event.resourceUid(), event);
    }

    public boolean containsEventUnderProcessing(String uuid) {
        return eventsUnderProcessing.containsKey(uuid);
    }

    public void addEventUnderProcessing(CustomResourceEvent event) {
        eventsUnderProcessing.put(event.resourceUid(), event);
    }
    public CustomResourceEvent removeEventUnderProcessing(String uid) {
        return eventsUnderProcessing.remove(uid);
    }

}
