package io.javaoperatorsdk.operator.processing;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;

import java.util.*;

class EventBuffer {

    private final Map<String, List<Event>> events = new HashMap<>();

    public void addEvent(Event event) {
        String uid = event.getRelatedCustomResourceUid();
        List<Event> crEvents = events.computeIfAbsent(uid, (id) -> new ArrayList<>(1));
        crEvents.add(event);
    }

    public boolean newEventsExists(String resourceId) {
        return !events.get(resourceId).isEmpty();
    }

    public void putBackEvents(String resourceUid, List<Event> oldEvents) {
        events.get(resourceUid).addAll(0, oldEvents);
    }

    public boolean containsEvents(String customResourceId) {
        return events.get(customResourceId) != null;
    }

    public List<Event> getAndRemoveEventsForExecution(String resourceUid) {
        List<Event> crEvents = events.remove(resourceUid);
        return crEvents == null ? Collections.emptyList() : crEvents;
    }

    public void cleanup(String resourceUid) {
        events.remove(resourceUid);
    }
}
