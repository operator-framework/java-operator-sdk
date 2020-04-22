package com.github.containersolutions.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class EventStore {

    private final static Logger log = LoggerFactory.getLogger(EventStore.class);

    private final Map<String, CustomResourceEvent> eventsNotScheduledYet = new HashMap<>();
    private final Map<String, CustomResourceEvent> eventsUnderProcessing = new HashMap<>();

    public boolean containsNotScheduledEvent(String uuid) {
        return eventsNotScheduledYet.containsKey(uuid);
    }

    public CustomResourceEvent removeEventNotScheduledYet(String uid) {
        return eventsNotScheduledYet.remove(uid);
    }

    public void addOrReplaceEventAsNotScheduledYet(CustomResourceEvent event) {
        eventsNotScheduledYet.put(event.resourceUid(), event);
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
