package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.EventScheduler;
import io.fabric8.kubernetes.api.model.EventSource;
import io.fabric8.kubernetes.client.CustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultEventManager implements EventHandler, EventManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventManager.class);

    private Map<CustomResource, List<EventProducer>> eventSources = new ConcurrentHashMap<>();

    private EventScheduler eventScheduler;

    public DefaultEventManager(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    @Override
    public void handleEvent(Event event, EventProducer eventProducer) {
        eventScheduler.handleEvent(event);
    }

    // Registration should happen from the same thread within controller
    @Override
    public void registerEventProducer(CustomResource customResource, EventProducer eventProducer) {
        List<EventProducer> eventSourceList = eventSources.get(customResource);
        if (eventSourceList == null) {
            eventSourceList = new ArrayList<>(1);
            eventSources.put(customResource, eventSourceList);
        }
        eventSourceList.add(eventProducer);
        eventProducer.setEventHandler(this);
        eventProducer.eventProducerRegistered(customResource);
    }

    // todo think about concurrency when async de-registration happens
    @Override
    public void deRegisterEventProducer(CustomResource customResource, EventProducer eventProducer) {
        List<EventProducer> eventSourceList = eventSources.get(customResource);
        if (eventSourceList == null || !eventSourceList.contains(eventProducer)) {
            log.warn("Event producer: {} not found for custom resource: ", eventProducer, customResource);
        } else {
            eventSourceList.remove(eventProducer);
            eventProducer.eventProducerDeRegistered(customResource);
        }
    }

    @Override
    public List<EventProducer> getRegisteredEventSources(CustomResource customResource) {
        List<EventProducer> eventSourceList = eventSources.get(customResource);
        if (eventSourceList == null) {
            return Collections.emptyList();
        }
        return eventSourceList;
    }
}
