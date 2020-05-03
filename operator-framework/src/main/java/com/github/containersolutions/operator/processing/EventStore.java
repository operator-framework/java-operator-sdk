package com.github.containersolutions.operator.processing;

import java.util.HashMap;
import java.util.Map;

public class EventStore {

    private final Map<String, CustomResourceEvent> eventsNotScheduled = new HashMap<>();
    private final Map<String, CustomResourceEvent> eventsUnderProcessing = new HashMap<>();
    private final Map<String, Long> lastGeneration = new HashMap<>();

    public boolean containsNotScheduledEvent(String uuid) {
        return eventsNotScheduled.containsKey(uuid);
    }

    public CustomResourceEvent removeEventNotScheduled(String uid) {
        return eventsNotScheduled.remove(uid);
    }

    public void addOrReplaceEventAsNotScheduled(CustomResourceEvent event) {
        eventsNotScheduled.put(event.resourceUid(), event);
        updateLastGeneration(event);
    }

    public boolean containsEventUnderProcessing(String uuid) {
        return eventsUnderProcessing.containsKey(uuid);
    }

    public void addEventUnderProcessing(CustomResourceEvent event) {
        eventsUnderProcessing.put(event.resourceUid(), event);
        updateLastGeneration(event);
    }

    public CustomResourceEvent removeEventUnderProcessing(String uid) {
        return eventsUnderProcessing.remove(uid);
    }

    private void updateLastGeneration(CustomResourceEvent event) {
        Long generation = event.getResource().getMetadata().getGeneration();
        Long storedGeneration = lastGeneration.get(event.getResource().getMetadata().getUid());
        if (storedGeneration == null || generation > storedGeneration) {
            lastGeneration.put(event.getResource().getMetadata().getUid(), generation);
        }
    }

    public boolean hasLargerGenerationThanLastStored(CustomResourceEvent event) {
        return getLastStoredGeneration(event) == null || getLastStoredGeneration(event) <
                event.getResource().getMetadata().getGeneration();
    }

    public Long getLastStoredGeneration(CustomResourceEvent event) {
        return lastGeneration.get(event.getResource().getMetadata().getUid());
    }
}
