package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.EventScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventManager implements EventHandler, EventSourceManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private Map<String, List<EventSource>> eventSources = new ConcurrentHashMap<>();

    private EventScheduler eventScheduler;

    public EventManager(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void handleEvent(Event event, EventSource eventSource) {
        eventScheduler.scheduleEvent(event);
    }

    // Registration should happen from the same thread within controller
    @Override
    public void registerEventProducer(String customResourceUid, EventSource eventSource) {
        List<EventSource> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null) {
            eventSourceList = new ArrayList<>(1);
            eventSources.put(customResourceUid, eventSourceList);
        }
        eventSourceList.add(eventSource);
        eventSource.setEventHandler(this);
        eventSource.eventSourceRegistered(customResourceUid);
    }

    // todo think about concurrency when async de-registration happens
    @Override
    public void deRegisterEventProducer(String customResourceUid, EventSource eventSource) {
        List<EventSource> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null || !eventSourceList.contains(eventSource)) {
            log.warn("Event producer: {} not found for custom resource: {}", eventSource, customResourceUid);
        } else {
            eventSourceList.remove(eventSource);
            eventSource.eventSourceDeRegistered(customResourceUid);
        }
    }

    @Override
    public List<EventSource> getRegisteredEventSources(String customResourceUid) {
        List<EventSource> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null) {
            return Collections.emptyList();
        }
        return eventSourceList;
    }

    public void eventProcessingFinished(ExecutionDescriptor executionDescriptor) {

    }
}
