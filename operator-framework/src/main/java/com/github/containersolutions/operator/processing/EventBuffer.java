package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.source.CustomResourceEvent;
import com.github.containersolutions.operator.processing.event.Event;

import java.util.*;

public class EventBuffer {

    private Map<String, List<Event>> events = new HashMap<>();
    private Map<String, CustomResourceEvent> latestCustomResourceEvent = new HashMap<>();

    public void addEvent(Event event) {
        String uid = event.getRelatedCustomResourceUid();
        if (event instanceof CustomResourceEvent) {
            latestCustomResourceEvent.put(uid, (CustomResourceEvent) event);
        } else {
            List<Event> crEvents = events.get(uid);
            if (crEvents == null) {
                crEvents = new ArrayList<>(1);
                events.put(uid, crEvents);
            }
            crEvents.add(event);
        }
    }

    public boolean containsEvents(String customResourceId) {
        return events.get(customResourceId) != null || latestCustomResourceEvent.get(customResourceId) != null;
    }

    public List<Event> getAndRemoveEventsForExecution(String resourceUid) {
        List<Event> crEvents = events.remove(resourceUid);
        if (crEvents == null) {
            crEvents = Collections.emptyList();
        }
        List<Event> result = new ArrayList<>(crEvents.size() + 1);
        CustomResourceEvent customResourceEvent = latestCustomResourceEvent.get(resourceUid);
        if (customResourceEvent != null) {
            result.add(customResourceEvent);
            latestCustomResourceEvent.remove(resourceUid);
        }
        return result;
    }
}
