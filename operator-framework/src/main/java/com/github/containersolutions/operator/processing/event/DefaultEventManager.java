package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.EventScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultEventManager implements EventHandler, EventProducerManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventManager.class);

    private Map<String, List<EventProducer>> eventSources = new ConcurrentHashMap<>();

    private EventScheduler eventScheduler;

    public DefaultEventManager(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void handleEvent(Event event, EventProducer eventProducer) {
        eventScheduler.scheduleEvent(event);
    }

    // Registration should happen from the same thread within controller
    @Override
    public void registerEventProducer(String customResourceUid, EventProducer eventProducer) {
        List<EventProducer> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null) {
            eventSourceList = new ArrayList<>(1);
            eventSources.put(customResourceUid, eventSourceList);
        }
        eventSourceList.add(eventProducer);
        eventProducer.setEventHandler(this);
        eventProducer.eventProducerRegistered(customResourceUid);
    }

    // todo think about concurrency when async de-registration happens
    @Override
    public void deRegisterEventProducer(String customResourceUid, EventProducer eventProducer) {
        List<EventProducer> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null || !eventSourceList.contains(eventProducer)) {
            log.warn("Event producer: {} not found for custom resource: {}", eventProducer, customResourceUid);
        } else {
            eventSourceList.remove(eventProducer);
            eventProducer.eventProducerDeRegistered(customResourceUid);
        }
    }

    @Override
    public List<EventProducer> getRegisteredEventProducers(String customResourceUid) {
        List<EventProducer> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null) {
            return Collections.emptyList();
        }
        return eventSourceList;
    }
}
