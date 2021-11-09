package io.javaoperatorsdk.operator.processing.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.LifecycleAware;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.EventProcessor;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;

public class EventSourceManager<R extends CustomResource<?, ?>>
    implements EventSourceRegistry<R>, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final Set<EventSource> eventSources = Collections.synchronizedSet(new HashSet<>());
  private EventProcessor<R> eventProcessor;
  private TimerEventSource<R> retryAndRescheduleTimerEventSource;
  private CustomResourceEventSource<R> customResourceEventSource;

  EventSourceManager() {
    init();
  }

  public EventSourceManager(Controller<R> controller) {
    init();
    customResourceEventSource = new CustomResourceEventSource<>(controller);
    registerEventSource(customResourceEventSource);
  }

  private void init() {
    this.retryAndRescheduleTimerEventSource = new TimerEventSource<>();
    registerEventSource(retryAndRescheduleTimerEventSource);
  }

  public EventSourceManager<R> setEventProcessor(EventProcessor<R> eventProcessor) {
    this.eventProcessor = eventProcessor;
    if (customResourceEventSource != null) {
      customResourceEventSource.setEventHandler(eventProcessor);
    }
    if (retryAndRescheduleTimerEventSource != null) {
      retryAndRescheduleTimerEventSource.setEventHandler(eventProcessor);
    }
    return this;
  }

  @Override
  public void start() throws OperatorException {
    lock.lock();
    try {
      log.debug("Starting event sources.");
      for (var eventSource : eventSources) {
        try {
          eventSource.start();
        } catch (Exception e) {
          log.warn("Error closing {} -> {}", eventSource, e);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void stop() {
    lock.lock();
    try {
      log.debug("Closing event sources.");
      for (var eventSource : eventSources) {
        try {
          eventSource.stop();
        } catch (Exception e) {
          log.warn("Error closing {} -> {}", eventSource, e);
        }
      }
      eventSources.clear();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final void registerEventSource(EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    lock.lock();
    try {
      eventSources.add(eventSource);
      eventSource.setEventHandler(eventProcessor);
    } catch (Throwable e) {
      if (e instanceof IllegalStateException || e instanceof MissingCRDException) {
        // leave untouched
        throw e;
      }
      throw new OperatorException(
          "Couldn't register event source: " + eventSource.getClass().getName(), e);
    } finally {
      lock.unlock();
    }
  }

  public void cleanupForCustomResource(CustomResourceID customResourceUid) {
    lock.lock();
    try {
      for (EventSource eventSource : this.eventSources) {
        eventSource.cleanupForCustomResource(customResourceUid);
      }
    } finally {
      lock.unlock();
    }
  }

  public TimerEventSource<R> getRetryAndRescheduleTimerEventSource() {
    return retryAndRescheduleTimerEventSource;
  }

  @Override
  public Set<EventSource> getRegisteredEventSources() {
    return Collections.unmodifiableSet(eventSources);
  }

  @Override
  public CustomResourceEventSource<R> getCustomResourceEventSource() {
    return customResourceEventSource;
  }

}
