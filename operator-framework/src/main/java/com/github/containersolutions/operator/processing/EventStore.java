package com.github.containersolutions.operator.processing;

import java.util.HashMap;
import java.util.Map;

public class EventStore {

    private final Map<String, CustomResourceEvent> eventsNotScheduled = new HashMap<>();
    private final Map<String, CustomResourceEvent> eventsUnderProcessing = new HashMap<>();
    private final Map<String, Long> lastGeneration = new HashMap<>();
    private final Map<String, CustomResourceEvent> receivedLastEventForGenerationAwareRetry = new HashMap<>();

    private final Map<String, Boolean> startedProcessingWithFinalizer = new HashMap<>();
    private final Map<String, Boolean> successFullyProcessedWithFinalizer = new HashMap<>();

    public boolean containsNotScheduledEvent(String uuid) {
        return eventsNotScheduled.containsKey(uuid);
    }

    public CustomResourceEvent removeEventNotScheduled(String uid) {
        return eventsNotScheduled.remove(uid);
    }

    public void addOrReplaceEventAsNotScheduledAndUpdateLastGeneration(CustomResourceEvent event) {
        eventsNotScheduled.put(event.resourceUid(), event);
        updateLastGeneration(event);
    }

    public boolean containsEventUnderProcessing(String uuid) {
        return eventsUnderProcessing.containsKey(uuid);
    }

    public void addEventUnderProcessingAndUpdateLastGeneration(CustomResourceEvent event) {
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

    public void addLastEventForGenerationAwareRetry(CustomResourceEvent event) {
        receivedLastEventForGenerationAwareRetry.put(event.resourceUid(), event);
    }

    public CustomResourceEvent getReceivedLastEventForGenerationAwareRetry(String uuid) {
        return receivedLastEventForGenerationAwareRetry.get(uuid);
    }

    public void markStartedProcessingWithFinalizerOnResource(CustomResourceEvent event) {
        startedProcessingWithFinalizer.put(event.resourceUid(),true);
    }

    public boolean startedProcessingWithFinalizerOnResource(CustomResourceEvent event) {
        Boolean res = startedProcessingWithFinalizer.get(event.resourceUid());
        return res == null ? false : res;
    }

    public boolean successfullyProcessedWithFinalizer(CustomResourceEvent event) {
        Boolean res = successFullyProcessedWithFinalizer.get(event.resourceUid());
        return res == null ? false : res;
    }

    public void markSuccessfullyProcessedWitFinalizer(String uuid) {
        successFullyProcessedWithFinalizer.put(uuid, true);
    }

    public void cleanup(String uuid) {
        lastGeneration.remove(uuid);
        receivedLastEventForGenerationAwareRetry.remove(uuid);
        successFullyProcessedWithFinalizer.remove(uuid);
        startedProcessingWithFinalizer.remove(uuid);
    }
}
