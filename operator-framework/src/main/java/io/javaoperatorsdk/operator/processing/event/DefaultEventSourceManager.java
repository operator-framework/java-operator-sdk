package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultEventSourceManager implements EventSourceManager {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private Map<String, EventSource> eventSources = new ConcurrentHashMap<>();
  private CustomResourceEventSource customResourceEventSource;
  private DefaultEventHandler defaultEventHandler;

  public DefaultEventSourceManager(DefaultEventHandler defaultEventHandler) {
    this.defaultEventHandler = defaultEventHandler;
  }

  public void registerCustomResourceEventSource(
      CustomResourceEventSource customResourceEventSource) {
    this.customResourceEventSource = customResourceEventSource;
    this.customResourceEventSource.addedToEventManager();
  }

  @Override
  public <T extends EventSource> void registerEventSource(String name, T eventSource) {
    try {
      lock.lock();
      EventSource currentEventSource = eventSources.get(name);
      if (currentEventSource != null) {
        throw new IllegalStateException(
            "Event source with name already registered. Event source name: " + name);
      }
      eventSources.put(name, eventSource);
      eventSource.setEventHandler(defaultEventHandler);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<EventSource> deRegisterCustomResourceFromEventSource(
      String eventSourceName, String customResourceUid) {
    try {
      lock.lock();
      EventSource eventSource = this.eventSources.get(eventSourceName);
      if (eventSource == null) {
        log.warn(
            "Event producer: {} not found for custom resource: {}",
            eventSourceName,
            customResourceUid);
        return Optional.empty();
      } else {
        eventSource.eventSourceDeRegisteredForResource(customResourceUid);
        return Optional.of(eventSource);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Map<String, EventSource> getRegisteredEventSources() {
    return Collections.unmodifiableMap(eventSources);
  }

  public void cleanup(String customResourceUid) {
    getRegisteredEventSources()
        .keySet()
        .forEach(k -> deRegisterCustomResourceFromEventSource(k, customResourceUid));
    eventSources.remove(customResourceUid);
  }
}
