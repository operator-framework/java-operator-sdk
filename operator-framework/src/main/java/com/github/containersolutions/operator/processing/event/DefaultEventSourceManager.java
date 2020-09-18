package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.processing.ProcessingUtils;
import com.github.containersolutions.operator.processing.event.source.EventSource;
import com.github.containersolutions.operator.processing.event.source.EventSourceManager;
import io.fabric8.kubernetes.client.CustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultEventSourceManager implements EventSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

    private Map<String, List<EventSource>> eventSources = new ConcurrentHashMap<>();

    private EventScheduler eventScheduler;

    public DefaultEventSourceManager(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    // Registration should happen from the same thread within controller
    @Override
    public void registerEventSource(CustomResource resource, EventSource eventSource) {
        List<EventSource> eventSourceList = eventSources.get(ProcessingUtils.getUID(resource));
        if (eventSourceList == null) {
            eventSourceList = new ArrayList<>(1);
            eventSources.put(ProcessingUtils.getUID(resource), eventSourceList);
        }
        eventSourceList.add(eventSource);
        eventSource.setEventHandler(eventScheduler);
        eventSource.eventSourceRegisteredForResource(resource);
    }

    // todo think about concurrency when async de-registration happens
    @Override
    public void deRegisterEventProducer(String customResourceUid, EventSource eventSource) {
        List<EventSource> eventSourceList = eventSources.get(customResourceUid);
        if (eventSourceList == null || !eventSourceList.contains(eventSource)) {
            log.warn("Event producer: {} not found for custom resource: {}", eventSource, customResourceUid);
        } else {
            eventSourceList.remove(eventSource);
            eventSource.eventSourceDeRegisteredForResource(customResourceUid);
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

    public void publishEventProcessingFinished(ExecutionDescriptor executionDescriptor) {

    }
}
