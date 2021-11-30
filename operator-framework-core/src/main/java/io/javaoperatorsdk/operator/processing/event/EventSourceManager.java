package io.javaoperatorsdk.operator.processing.event;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.LifecycleAwareEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

public class EventSourceManager<R extends HasMetadata>
    implements EventSourceRegistry<R>, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  // This needs to be a list since the event source must be started in a deterministic order. The
  // controllerResourceEventSource must be always the first to have informers available for other
  // informers to access the main controller cache.
  private final List<EventSource> eventSources = Collections.synchronizedList(new ArrayList<>());
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

  public void broadcastOnResourceEvent(ResourceAction action, R resource, R oldResource) {
    lock.lock();
    try {
      for (EventSource eventSource : this.eventSources) {
        if (eventSource instanceof LifecycleAwareEventSource) {
          var lifecycleAwareES = ((ResourceEventAware<R>) eventSource);
          switch (action) {
            case ADDED:
              lifecycleAwareES.onResourceCreated(resource);
              break;
            case UPDATED:
              lifecycleAwareES.onResourceUpdated(resource, oldResource);
              break;
            case DELETED:
              lifecycleAwareES.onResourceDeleted(resource);
              break;
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Set<EventSource> getRegisteredEventSources() {
    return new HashSet<>(eventSources);
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
