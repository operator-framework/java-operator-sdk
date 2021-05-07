package io.javaoperatorsdk.operator.processing.event;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultEventSourceManager implements EventSourceManager {

  public static final String RETRY_TIMER_EVENT_SOURCE_NAME = "retry-timer-event-source";
  private static final String CUSTOM_RESOURCE_EVENT_SOURCE_NAME = "custom-resource-event-source";
  private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final Map<String, EventSource> eventSources = new ConcurrentHashMap<>();
  private final DefaultEventHandler defaultEventHandler;
  private TimerEventSource retryTimerEventSource;

  DefaultEventSourceManager(DefaultEventHandler defaultEventHandler, boolean supportRetry) {
    this.defaultEventHandler = defaultEventHandler;
    defaultEventHandler.setEventSourceManager(this);
    if (supportRetry) {
      this.retryTimerEventSource = new TimerEventSource();
      registerEventSource(RETRY_TIMER_EVENT_SOURCE_NAME, retryTimerEventSource);
    }
  }

  public <R extends CustomResource<?, ?>> DefaultEventSourceManager(
      ResourceController<R> controller,
      ControllerConfiguration<R> configuration,
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client) {
    this(new DefaultEventHandler(controller, configuration, client), true);
    registerEventSource(
        CUSTOM_RESOURCE_EVENT_SOURCE_NAME, new CustomResourceEventSource<>(client, configuration));
  }

  @Override
  public void close() {
    try {
      lock.lock();
      for (var entry : eventSources.entrySet()) {
        try {
          log.debug("Closing {} -> {}", entry.getKey(), entry.getValue());
          entry.getValue().close();
        } catch (Exception e) {
          log.warn("Error closing {} -> {}", entry.getKey(), entry.getValue(), e);
        }
      }

      eventSources.clear();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final void registerEventSource(String name, EventSource eventSource) {
    Objects.requireNonNull(eventSource, "EventSource must not be null");

    try {
      lock.lock();
      if (eventSources.containsKey(name)) {
        throw new IllegalStateException(
            "Event source with name already registered. Event source name: " + name);
      }
      eventSources.put(name, eventSource);
      eventSource.setEventHandler(defaultEventHandler);
      eventSource.start();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<EventSource> deRegisterEventSource(String name) {
    try {
      lock.lock();
      EventSource currentEventSource = eventSources.remove(name);
      if (currentEventSource != null) {
        currentEventSource.close();
      }

      return Optional.ofNullable(currentEventSource);
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

  public TimerEventSource getRetryTimerEventSource() {
    return retryTimerEventSource;
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
