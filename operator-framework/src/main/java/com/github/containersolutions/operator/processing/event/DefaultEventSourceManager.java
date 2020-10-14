package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.processing.ProcessingUtils;
import com.github.containersolutions.operator.processing.event.internal.CustomResourceEventSource;
import io.fabric8.kubernetes.client.CustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class DefaultEventSourceManager implements EventSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, Map<String, EventSource>> eventSources = new ConcurrentHashMap<>();
    private CustomResourceEventSource customResourceEventSource;
    private EventScheduler eventScheduler;


    public DefaultEventSourceManager(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    public void registerCustomResourceEventSource(CustomResourceEventSource customResourceEventSource) {
        this.customResourceEventSource = customResourceEventSource;
        this.customResourceEventSource.addedToEventManager();
    }

    // Registration should happen from the same thread within controller
    @Override
    public void registerEventSource(CustomResource resource, String name, EventSource eventSource) {
        try {
            lock.lock();
            Map<String, EventSource> eventSourceList = eventSources.get(ProcessingUtils.getUID(resource));
            if (eventSourceList == null) {
                eventSourceList = new HashMap<>(1);
                eventSources.put(ProcessingUtils.getUID(resource), eventSourceList);
            }
            eventSourceList.put(name, eventSource);
            eventSource.setEventHandler(eventScheduler);
            eventSource.eventSourceRegisteredForResource(resource);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<EventSource> deRegisterEventSource(String customResourceUid, String name) {
        try {
            lock.lock();
            Map<String, EventSource> eventSources = this.eventSources.get(customResourceUid);
            if (eventSources == null || !eventSources.containsKey(name)) {
                log.warn("Event producer: {} not found for custom resource: {}", name, customResourceUid);
                return Optional.empty();
            } else {
                EventSource eventSource = eventSources.remove(name);
                eventSource.eventSourceDeRegisteredForResource(customResourceUid);
                return Optional.of(eventSource);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, EventSource> getRegisteredEventSources(String customResourceUid) {
        Map<String, EventSource> eventSourceMap = eventSources.get(customResourceUid);
        return eventSourceMap != null ? eventSourceMap : Collections.EMPTY_MAP;
    }

    public void eventProcessingFinished(ExecutionDescriptor executionDescriptor) {
        String uid = executionDescriptor.getExecutionScope().getCustomResourceUid();
        Map<String, EventSource> sources = getRegisteredEventSources(uid);
        sources.values().forEach(es -> es.controllerExecuted(executionDescriptor));
    }

    public void cleanup(String customResourceUid) {
        getRegisteredEventSources(customResourceUid).keySet().forEach(k -> deRegisterEventSource(customResourceUid, k));
        eventSources.remove(customResourceUid);
    }
}
