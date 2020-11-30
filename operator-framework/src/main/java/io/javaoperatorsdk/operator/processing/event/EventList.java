package io.javaoperatorsdk.operator.processing.event;

import java.util.*;
import java.util.stream.Collectors;

public class EventList {

    private final List<Event> eventList;

    public EventList(List<Event> eventList) {
        this.eventList = eventList;
    }

    List<Event> getEventList() {
        return eventList;
    }

    public <T extends Event> Optional<T> getLatestOfType(Class<T> eventType) {
        ListIterator<Event> iterator = eventList.listIterator(eventList.size() - 1);
        while (iterator.hasPrevious()) {
            Event event = iterator.previous();
            if (event.getClass().isAssignableFrom(eventType)) {
                return Optional.of((T)event);
            }
        }

        List<Event> eventsOfType = eventList.stream()
                .filter(event -> event.getClass().isAssignableFrom(eventType))
                .collect(Collectors.toList());
        if (eventsOfType.size() > 0) {
            return Optional.of((T) eventsOfType.get(eventsOfType.size() - 1));
        } else {
            return Optional.empty();
        }
    }
}
