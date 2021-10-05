package io.javaoperatorsdk.operator.processing.event;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;

public class DefaultEventSourceManager<R extends CustomResource<?, ?>>
    implements EventSourceManager {

  public static final String RETRY_TIMER_EVENT_SOURCE_NAME = "retry-timer-event-source";
  public static final String CUSTOM_RESOURCE_EVENT_SOURCE_NAME = "custom-resource-event-source";
  private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final Map<String, EventSource> eventSources = new ConcurrentHashMap<>();
  private DefaultEventHandler<R> defaultEventHandler;
  private TimerEventSource<R> retryTimerEventSource;

  DefaultEventSourceManager(DefaultEventHandler<R> defaultEventHandler) {
    init(defaultEventHandler);
  }

  public DefaultEventSourceManager(ConfiguredController<R> controller) {
    CustomResourceEventSource customResourceEventSource =
        new CustomResourceEventSource<>(controller);
    init(new DefaultEventHandler<>(controller, customResourceEventSource));
    registerEventSource(CUSTOM_RESOURCE_EVENT_SOURCE_NAME, customResourceEventSource);
  }

  private void init(DefaultEventHandler<R> defaultEventHandler) {
    this.defaultEventHandler = defaultEventHandler;
    defaultEventHandler.setEventSourceManager(this);

    this.retryTimerEventSource = new TimerEventSource<>();
    registerEventSource(RETRY_TIMER_EVENT_SOURCE_NAME, retryTimerEventSource);
  }

  @Override
  public void close() {
    try {
      lock.lock();

      try {
        defaultEventHandler.close();
      } catch (Exception e) {
        log.warn("Error closing event handler", e);
      }

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
  public final void registerEventSource(String name, EventSource eventSource)
      throws OperatorException {
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
    } catch (Throwable e) {
      if (e instanceof IllegalStateException || e instanceof MissingCRDException) {
        // leave untouched
        throw e;
      }
      throw new OperatorException("Couldn't register event source named '" + name + "'", e);
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
        try {
          currentEventSource.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      return Optional.ofNullable(currentEventSource);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<EventSource> deRegisterCustomResourceFromEventSource(
      String eventSourceName, CustomResourceID customResourceUid) {
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

  public void cleanup(CustomResourceID customResourceUid) {
    getRegisteredEventSources()
        .keySet()
        .forEach(k -> deRegisterCustomResourceFromEventSource(k, customResourceUid));
  }

  // // todo: remove
  // public ResourceCache getCache() {
  // final var source =
  // (CustomResourceEventSource) getRegisteredEventSources()
  // .get(CUSTOM_RESOURCE_EVENT_SOURCE_NAME);
  // return source;
  // }

  // // todo: remove
  // public Optional<CustomResource> getLatestResource(String customResourceUid) {
  // return getCache().getLatestResource(customResourceUid);
  // }
  //
  // // todo: remove
  // public List<CustomResource> getLatestResources(Predicate<CustomResource> selector) {
  // return getCache().getLatestResources(selector);
  // }
  //
  // // todo: remove
  // public Set<String> getLatestResourceUids(Predicate<CustomResource> selector) {
  // return getCache().getLatestResourcesUids(selector);
  // }
  //
  // // todo: remove
  // public void cacheResource(CustomResource resource, Predicate<CustomResource> predicate) {
  // getCache().cacheResource(resource, predicate);
  // }
}
