package io.javaoperatorsdk.operator.processing.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.TimerEventSource;

public class EventSourceManager<R extends HasMetadata>
    implements EventSourceRegistry<R>, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final Set<EventSource> eventSources = Collections.synchronizedSet(new HashSet<>());
  private final EventProcessor<R> eventProcessor;
  private TimerEventSource<R> retryAndRescheduleTimerEventSource;
  private ControllerResourceEventSource<R> controllerResourceEventSource;
  private final Controller<R> controller;

  EventSourceManager(EventProcessor<R> eventProcessor) {
    this.eventProcessor = eventProcessor;
    controller = null;
    initRetryEventSource();
  }

  public EventSourceManager(Controller<R> controller) {
    this.controller = controller;
    controllerResourceEventSource = new ControllerResourceEventSource<>(controller);
    this.eventProcessor = new EventProcessor<>(this);
    registerEventSource(controllerResourceEventSource);
    initRetryEventSource();
  }

  private void initRetryEventSource() {
    retryAndRescheduleTimerEventSource = new TimerEventSource<>();
    registerEventSource(retryAndRescheduleTimerEventSource);
  }

  @Override
  public void start() throws OperatorException {
    eventProcessor.start();
    lock.lock();
    try {
      log.debug("Starting event sources.");
      for (var eventSource : eventSources) {
        try {
          eventSource.start();
        } catch (Exception e) {
          log.warn("Error starting {} -> {}", eventSource, e);
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
    eventProcessor.stop();
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

  public void cleanupForCustomResource(ResourceID customResourceUid) {
    lock.lock();
    try {
      for (EventSource eventSource : this.eventSources) {
        eventSource.cleanupForResource(customResourceUid);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Set<EventSource> getRegisteredEventSources() {
    return Collections.unmodifiableSet(eventSources);
  }

  @Override
  public ControllerResourceEventSource<R> getControllerResourceEventSource() {
    return controllerResourceEventSource;
  }

  TimerEventSource<R> retryEventSource() {
    return retryAndRescheduleTimerEventSource;
  }

  Controller<R> getController() {
    return controller;
  }
}
