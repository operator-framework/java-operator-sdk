package io.javaoperatorsdk.operator.processing.event;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

public class EventSourceManager<R extends HasMetadata> implements LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final ConcurrentNavigableMap<String, EventSource> eventSources =
      new ConcurrentSkipListMap<>();
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
      for (var eventSource : eventSources.values()) {
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
      for (var eventSource : eventSources.values()) {
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

  public final void registerEventSource(EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    lock.lock();
    try {
      eventSources.put(keyFor(eventSource), eventSource);
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

  private String keyFor(EventSource source) {
    return keyFor(
        source instanceof ResourceEventSource ? ((ResourceEventSource) source).getResourceClass()
            : source.getClass());
  }

  private String keyFor(Class<?> dependentType, String... qualifier) {
    final var className = dependentType.getCanonicalName();
    var key = className;
    if (qualifier != null && qualifier.length > 0) {
      key += "-" + qualifier[0];
    }

    // make sure timer event source is started first, then controller event source
    // this is needed so that these sources are set when informer sources start so that events can
    // properly be processed
    if (controllerResourceEventSource != null
        && className.equals(controllerResourceEventSource.getResourceClass().getCanonicalName())) {
      key = 1 + key;
    } else if (retryAndRescheduleTimerEventSource != null && className
        .equals(retryAndRescheduleTimerEventSource.getClass().getCanonicalName())) {
      key = 0 + key;
    }
    return key;
  }

  public void broadcastOnResourceEvent(ResourceAction action, R resource, R oldResource) {
    for (var eventSource : eventSources.values()) {
      if (eventSource instanceof ResourceEventAware) {
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
  }

  EventHandler getEventHandler() {
    return eventProcessor;
  }

  Set<EventSource> getRegisteredEventSources() {
    return new LinkedHashSet<>(eventSources.values());
  }

  public ControllerResourceEventSource<R> getControllerResourceEventSource() {
    return controllerResourceEventSource;
  }

  public <S> Optional<ResourceEventSource<R, S>> getResourceEventSourceFor(
      Class<S> dependentType, String... qualifier) {
    if (dependentType == null) {
      return Optional.empty();
    }
    final var eventSource = eventSources.get(keyFor(dependentType, qualifier));
    if (eventSource == null) {
      return Optional.empty();
    }
    if (!(eventSource instanceof ResourceEventSource)) {
      throw new IllegalArgumentException(eventSource + " associated with "
          + keyAsString(dependentType, qualifier) + " is not a "
          + ResourceEventSource.class.getSimpleName());
    }
    final var source = (ResourceEventSource<R, S>) eventSource;
    final var resourceClass = source.getResourceClass();
    if (!resourceClass.isAssignableFrom(dependentType)) {
      throw new IllegalArgumentException(eventSource + " associated with "
          + keyAsString(dependentType, qualifier)
          + " is handling " + resourceClass.getName() + " resources but asked for "
          + dependentType.getName());
    }
    return Optional.of((ResourceEventSource<R, S>) eventSource);
  }

  @SuppressWarnings("rawtypes")
  private String keyAsString(Class dependentType, String... qualifier) {
    return qualifier != null && qualifier.length > 0
        ? "(" + dependentType.getName() + ", " + qualifier[0] + ")"
        : dependentType.getName();
  }

  TimerEventSource<R> retryEventSource() {
    return retryAndRescheduleTimerEventSource;
  }

  Controller<R> getController() {
    return controller;
  }
}
