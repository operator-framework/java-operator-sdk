package io.javaoperatorsdk.operator.processing.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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
  private final EventSources<R> eventSources = new EventSources<>();
  private final EventProcessor<R> eventProcessor;
  private final Controller<R> controller;

  EventSourceManager(EventProcessor<R> eventProcessor) {
    this.eventProcessor = eventProcessor;
    controller = null;
    registerEventSource(eventSources.initRetryEventSource());
  }

  public EventSourceManager(Controller<R> controller) {
    this.controller = controller;
    // controller event source needs to be available before we create the event processor
    final var controllerEventSource = eventSources.initControllerEventSource(controller);
    this.eventProcessor = new EventProcessor<>(this);
    registerEventSource(eventSources.initRetryEventSource());
    registerEventSource(controllerEventSource);
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
    for (var eventSource : eventSources) {
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
    return eventSources.all();
  }

  public ControllerResourceEventSource<R> getControllerResourceEventSource() {
    return eventSources.controllerResourceEventSource;
  }

  public <S> Optional<ResourceEventSource<R, S>> getResourceEventSourceFor(
      Class<S> dependentType, String... qualifier) {
    if (dependentType == null) {
      return Optional.empty();
    }
    String name = (qualifier != null && qualifier.length >= 1) ? qualifier[0] : "";
    final var eventSource = eventSources.get(dependentType, name);
    return Optional.ofNullable(eventSource);
  }

  TimerEventSource<R> retryEventSource() {
    return eventSources.retryAndRescheduleTimerEventSource;
  }

  Controller<R> getController() {
    return controller;
  }

  private static class EventSources<R extends HasMetadata> implements Iterable<EventSource> {
    private final ConcurrentNavigableMap<String, List<EventSource>> sources =
        new ConcurrentSkipListMap<>();
    private final TimerEventSource<R> retryAndRescheduleTimerEventSource = new TimerEventSource<>();
    private ControllerResourceEventSource<R> controllerResourceEventSource;


    ControllerResourceEventSource<R> initControllerEventSource(Controller<R> controller) {
      controllerResourceEventSource = new ControllerResourceEventSource<>(controller);
      return controllerResourceEventSource;
    }

    TimerEventSource<R> initRetryEventSource() {
      return retryAndRescheduleTimerEventSource;
    }

    @Override
    public Iterator<EventSource> iterator() {
      return sources.values().stream().flatMap(Collection::stream).iterator();
    }

    public Set<EventSource> all() {
      return new LinkedHashSet<>(sources.values().stream().flatMap(Collection::stream)
          .collect(Collectors.toList()));
    }

    public void clear() {
      sources.clear();
    }

    public boolean contains(EventSource source) {
      final var eventSources = sources.get(keyFor(source));
      if (eventSources == null || eventSources.isEmpty()) {
        return false;
      }
      return findMatchingSource(qualifier(source), eventSources).isPresent();
    }

    public void add(EventSource eventSource) {
      if (contains(eventSource)) {
        throw new IllegalArgumentException("An event source is already registered for the "
            + keyAsString(getDependentType(eventSource), qualifier(eventSource))
            + " class/name combination");
      }
      sources.computeIfAbsent(keyFor(eventSource), k -> new ArrayList<>()).add(eventSource);
    }

    private Class getDependentType(EventSource source) {
      return source instanceof ResourceEventSource
          ? ((ResourceEventSource) source).getResourceClass()
          : source.getClass();
    }

    private String qualifier(EventSource source) {
      final var configuration = source.getConfiguration();
      return configuration != null ? configuration.name() : "";
    }

    private String keyFor(EventSource source) {
      return keyFor(getDependentType(source));
    }

    private String keyFor(Class<?> dependentType) {
      var key = dependentType.getCanonicalName();

      // make sure timer event source is started first, then controller event source
      // this is needed so that these sources are set when informer sources start so that events can
      // properly be processed
      if (controllerResourceEventSource != null
          && key.equals(controllerResourceEventSource.getResourceClass().getCanonicalName())) {
        key = 1 + "-" + key;
      } else if (key.equals(retryAndRescheduleTimerEventSource.getClass().getCanonicalName())) {
        key = 0 + "-" + key;
      }
      return key;
    }

    public <S> ResourceEventSource<R, S> get(Class<S> dependentType, String qualifier) {
      final var sourcesForType = sources.get(keyFor(dependentType));
      if (sourcesForType == null || sourcesForType.isEmpty()) {
        return null;
      }

      final var size = sourcesForType.size();
      final EventSource source;
      if (size == 1) {
        source = sourcesForType.get(0);
      } else {
        if (qualifier == null || qualifier.isBlank()) {
          throw new IllegalArgumentException("There are multiple EventSources registered for type "
              + dependentType.getCanonicalName()
              + ", you need to provide a qualifier to specify which EventSource you want to query. Known qualifiers: "
              + sourcesForType.stream().map(this::qualifier).collect(Collectors.joining(",")));
        }
        source = findMatchingSource(qualifier, sourcesForType).orElse(null);

        if (source == null) {
          return null;
        }
      }

      if (!(source instanceof ResourceEventSource)) {
        throw new IllegalArgumentException(source + " associated with "
            + keyAsString(dependentType, qualifier) + " is not a "
            + ResourceEventSource.class.getSimpleName());
      }
      final var res = (ResourceEventSource<R, S>) source;
      final var resourceClass = res.getResourceClass();
      if (!resourceClass.isAssignableFrom(dependentType)) {
        throw new IllegalArgumentException(source + " associated with "
            + keyAsString(dependentType, qualifier)
            + " is handling " + resourceClass.getName() + " resources but asked for "
            + dependentType.getName());
      }
      return res;
    }

    private Optional<EventSource> findMatchingSource(String qualifier,
        List<EventSource> sourcesForType) {
      return sourcesForType.stream().filter(es -> qualifier(es).equals(qualifier)).findAny();
    }

    @SuppressWarnings("rawtypes")
    private String keyAsString(Class dependentType, String qualifier) {
      return qualifier != null && qualifier.length() > 0
          ? "(" + dependentType.getName() + ", " + qualifier + ")"
          : dependentType.getName();
    }
  }
}
