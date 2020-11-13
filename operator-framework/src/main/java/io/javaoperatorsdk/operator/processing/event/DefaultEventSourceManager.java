package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.processing.ProcessingUtils;
import com.github.containersolutions.operator.processing.event.internal.CustomResourceEventSource;
import com.github.containersolutions.operator.processing.event.internal.DelayedEventSource;
import io.fabric8.kubernetes.client.CustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultEventSourceManager implements EventSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, Map<String, EventSource>> eventSources = new ConcurrentHashMap<>();
    private CustomResourceEventSource customResourceEventSource;
    private EventScheduler eventScheduler;

    private DelayedEventSource delayedEventSource = new DelayedEventSource();

    public DefaultEventSourceManager(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }

    public void registerCustomResourceEventSource(CustomResourceEventSource customResourceEventSource) {
        this.customResourceEventSource = customResourceEventSource;
        this.customResourceEventSource.addedToEventManager();
    }

    // Registration should happen from the same thread within controller
    @Override
    public <T extends EventSource> void registerEventSource(CustomResource customResource, String name, T eventSource) {
        try {
            lock.lock();
            Map<String, EventSource> eventSourceList = eventSources.get(ProcessingUtils.getUID(customResource));
            if (eventSourceList == null) {
                eventSourceList = new HashMap<>(1);
                eventSources.put(ProcessingUtils.getUID(customResource), eventSourceList);
            }
            if (eventSourceList.get(name) != null) {
                throw new IllegalStateException("Event source with name already registered. Resource id: "
                        + ProcessingUtils.getUID(customResource) + ", event source name: " + name);
            }
            eventSourceList.put(name, eventSource);
            eventSource.setEventHandler(eventScheduler);
            eventSource.eventSourceRegisteredForResource(customResource);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T extends EventSource> T registerEventSourceIfNotRegistered(CustomResource customResource, String name, T eventSource) {
        try {
            lock.lock();
            if (eventSources.get(ProcessingUtils.getUID(customResource)) == null ||
                    eventSources.get(ProcessingUtils.getUID(customResource)).get(name) == null) {
                registerEventSource(customResource, name, eventSource);
                return eventSource;
            }
            return (T) eventSources.get(ProcessingUtils.getUID(customResource)).get(name);
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

    public void controllerExecuted(ExecutionDescriptor executionDescriptor) {
        String uid = executionDescriptor.getExecutionScope().getCustomResourceUid();
        Map<String, EventSource> sources = getRegisteredEventSources(uid);
        sources.values().forEach(es -> es.controllerExecuted(executionDescriptor));
    }

    public void cleanup(String customResourceUid) {
        getRegisteredEventSources(customResourceUid).keySet().forEach(k -> deRegisterEventSource(customResourceUid, k));
        eventSources.remove(customResourceUid);
    }

    public DelayedEventSource getDelayedReprocessEventSource() {
        return delayedEventSource;
    }
}
