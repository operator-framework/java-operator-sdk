package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;
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
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

public class EventSourceManager<R extends HasMetadata>
    implements EventSourceRegistry<R>, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final ConcurrentNavigableMap<String, EventSource<R>> eventSources =
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
    initRetryEventSource();

    controllerResourceEventSource = new ControllerResourceEventSource<>(controller, this);
    this.eventProcessor = new EventProcessor<>(this);
    registerEventSource(controllerResourceEventSource);
  }

  private void initRetryEventSource() {
    retryAndRescheduleTimerEventSource = new TimerEventSource<>();
    registerEventSource(retryAndRescheduleTimerEventSource);
  }

  @Override
  public EventHandler getEventHandler() {
    return eventProcessor;
  }

  @Override
  public void start() throws OperatorException {
    eventProcessor.start();
    lock.lock();
    try {
      for (var eventSource : eventSources.values()) {
        try {
          log.debug("Starting source {} for {}", eventSource.getClass(),
              eventSource.getResourceClass());
          eventSource.start();
          log.debug("Source {} started", eventSource.getClass());
        } catch (Exception e) {
          if (e instanceof MissingCRDException) {
            throw e;
          }
          throw new OperatorException("Couldn't start source " + eventSource, e);
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

  @Override
  public final void registerEventSource(EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    lock.lock();
    try {
      eventSources.put(keyFor(eventSource), eventSource);
      eventSource.setEventRegistry(this);
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
    String name;
    if (source instanceof ResourceEventSource) {
      ResourceEventSource resourceEventSource = (ResourceEventSource) source;
      final var configuration = resourceEventSource.getConfiguration();
      // todo: extract qualifier from configuration
      name = keyFor(configuration.getResourceClass());
    } else {
      name = keyFor(source.getResourceClass());
    }
    return name;
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
        .equals(retryAndRescheduleTimerEventSource.getResourceClass().getCanonicalName())) {
      key = 0 + key;
    }
    return key;
  }

  public void broadcastOnResourceEvent(ResourceAction action, R resource, R oldResource) {
    for (EventSource eventSource : this.eventSources.values()) {
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

  @Override
  public Set<EventSource<R>> getRegisteredEventSources() {
    return Set.copyOf(eventSources.values());
  }

  @Override
  public ControllerResourceEventSource<R> getControllerResourceEventSource() {
    return controllerResourceEventSource;
  }

  @Override
  public <S extends HasMetadata> ResourceEventSource<S, R> getResourceEventSourceFor(
      Class<S> dependentType,
      String... qualifier) {
    final var eventSource = eventSources.get(keyFor(dependentType, qualifier));
    if (eventSource == null) {
      return null;
    }
    if (!(eventSource instanceof ResourceEventSource)) {
      throw new IllegalArgumentException(eventSource + " associated with "
          + keyAsString(dependentType, qualifier) + " is not a "
          + ResourceEventSource.class.getSimpleName());
    }
    final var source = (ResourceEventSource<S, R>) eventSource;
    final var configuration = source.getConfiguration();
    final var resourceClass = configuration.getResourceClass();
    if (!resourceClass.isAssignableFrom(dependentType)) {
      throw new IllegalArgumentException(eventSource + " associated with "
          + keyAsString(dependentType, qualifier)
          + " is handling " + resourceClass.getName() + " resources but asked for "
          + dependentType.getName());
    }
    return (ResourceEventSource<S, R>) eventSource;
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
