package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.CustomResourceEvent;
import com.github.containersolutions.operator.processing.event.Event;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.*;

public class EventBuffer {

    private Map<String, List<Event>> events = new HashMap<>();
    private Map<String, CustomResourceEvent> latestCustomResourceEvent = new HashMap<>();

    public void addEvent(Event event) {
        String uid = event.getRelatedCustomResourceUid();
        List<Event> crEvents = events.get(uid);
        if (crEvents == null) {
            crEvents = new ArrayList<>(1);
            events.put(uid, crEvents);
        }
        crEvents.add(event);
    }

    public List<Event> getAndRemoveEventsForExecution(CustomResource resource) {
        String uid = ProcessingUtils.getUID(resource);
        List<Event> crEvents = events.remove(uid);
        if (crEvents == null) {
            crEvents = Collections.emptyList();
        }
        List<Event> result = new ArrayList<>(crEvents.size()+1);

        CustomResourceEvent customResourceEvent = latestCustomResourceEvent.get(uid);


        return result;
    }

    public void addOrUpdateLatestCustomResourceEvent(CustomResourceEvent customResourceEvent) {
        latestCustomResourceEvent.put(
                ProcessingUtils.getUID(customResourceEvent.getCustomResource()), customResourceEvent);
    }


}
