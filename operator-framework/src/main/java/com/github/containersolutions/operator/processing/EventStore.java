package com.github.containersolutions.operator.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class EventStore {

    private final static Logger log = LoggerFactory.getLogger(EventStore.class);

    private final Map<String, Long> lastResourceVersion = new HashMap<>();

    private final Map<String, CustomResourceEvent> eventsNotScheduledYet = new HashMap<>();
    private final Map<String, CustomResourceEvent> eventsUnderProcessing = new HashMap<>();

    public boolean containsOlderVersionOfNotScheduledEvent(CustomResourceEvent newEvent) {
        return eventsNotScheduledYet.containsKey(newEvent.resourceUid()) &&
                newEvent.isSameResourceAndNewerVersion(eventsNotScheduledYet.get(newEvent.resourceUid()));
    }

    public CustomResourceEvent removeEventNotScheduledYet(String uid) {
        return eventsNotScheduledYet.remove(uid);
    }

    public void addOrReplaceEventAsNotScheduledYet(CustomResourceEvent event) {
        eventsNotScheduledYet.put(event.resourceUid(), event);
    }

    public boolean containsOlderVersionOfEventUnderProcessing(CustomResourceEvent newEvent) {
        return eventsUnderProcessing.containsKey(newEvent.resourceUid()) &&
                newEvent.isSameResourceAndNewerVersion(eventsUnderProcessing.get(newEvent.resourceUid()));
    }


    public void addEventUnderProcessing(CustomResourceEvent event) {
        eventsUnderProcessing.put(event.resourceUid(), event);
    }

    public CustomResourceEvent removeEventUnderProcessing(String uid) {
        return eventsUnderProcessing.remove(uid);
    }

    public void updateLatestResourceVersionProcessed(CustomResourceEvent event) {
        Long current = lastResourceVersion.get(event.resourceUid());
        long received = Long.parseLong(event.getResource().getMetadata().getResourceVersion());
        if (current == null || received > current) {
            lastResourceVersion.put(event.resourceUid(), received);
            log.debug("Resource version for {} updated from {} to {}", event.getResource().getMetadata().getName(), current, received);
        } else {
            log.debug("Resource version for {} not updated from {}", event.getResource().getMetadata().getName(), current);
        }
    }

    public boolean processedNewerVersionBefore(CustomResourceEvent customResourceEvent) {
        Long lastVersionProcessed = lastResourceVersion.get(customResourceEvent.resourceUid());
        if (lastVersionProcessed == null) {
            return false;
        } else {
            return lastVersionProcessed > Long.parseLong(customResourceEvent.getResource()
                    .getMetadata().getResourceVersion());
        }
    }
}
