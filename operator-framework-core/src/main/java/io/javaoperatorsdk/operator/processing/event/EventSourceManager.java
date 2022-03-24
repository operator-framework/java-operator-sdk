package io.javaoperatorsdk.operator.processing.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    registerEventSource(eventSources.retryEventSource());
  }

  public EventSourceManager(Controller<R> controller) {
    this.controller = controller;
    // controller event source needs to be available before we create the event processor
    final var controllerEventSource = eventSources.initControllerEventSource(controller);
    this.eventProcessor = new EventProcessor<>(this);

    // sources need to be registered after the event processor is created since it's set on the
    // event source
    registerEventSource(eventSources.retryEventSource());
    registerEventSource(controllerEventSource);
  }

  /**
   * Starts the event sources first and then the processor. Note that it's not desired to start
   * processing events while the event sources are not "synced". This not fully started and the
   * caches propagated - although for non k8s related event sources this behavior might be different
   * (see
   * {@link io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource}).
   *
   * Now the event sources are also started sequentially, mainly because others might depend on
   * {@link ControllerResourceEventSource} , which is started first.
   */
  @Override
  public void start() {
    lock.lock();
    try {
      for (var eventSource : eventSources) {
        try {
          logEventSourceEvent(eventSource, "Starting");
          eventSource.start();
          logEventSourceEvent(eventSource, "Started");
        } catch (MissingCRDException e) {
          throw e; // leave untouched
        } catch (Exception e) {
          throw new OperatorException("Couldn't start source " + eventSource.name(), e);
        }
      }
      eventProcessor.start();
    } finally {
      lock.unlock();
    }
  }

  @SuppressWarnings("rawtypes")
  private void logEventSourceEvent(NamedEventSource eventSource, String event) {
    if (log.isDebugEnabled()) {
      if (eventSource instanceof ResourceEventSource) {
        ResourceEventSource source = (ResourceEventSource) eventSource;
        log.debug("{} event source {} for {}", event, eventSource.name(),
            source.getResourceClass());
      } else {
        log.debug("{} event source {}", event, eventSource.name());
      }
    }
  }

  @Override
  public void stop() {
    lock.lock();
    try {
      for (var eventSource : eventSources) {
        try {
          logEventSourceEvent(eventSource, "Stopping");
          eventSource.stop();
          logEventSourceEvent(eventSource, "Stopped");
        } catch (Exception e) {
          log.warn("Error closing {} -> {}", eventSource.name(), e);
        }
      }
      eventSources.clear();
    } finally {
      lock.unlock();
    }
    eventProcessor.stop();
  }

  public final void registerEventSource(EventSource eventSource) throws OperatorException {
    registerEventSource(null, eventSource);
  }

  public final void registerEventSource(String name, EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    lock.lock();
    try {
      if (name == null || name.isBlank()) {
        name = EventSource.defaultNameFor(eventSource);
      }
      eventSources.add(name, eventSource);
      eventSource.setEventHandler(eventProcessor);
    } catch (IllegalStateException | MissingCRDException e) {
      throw e; // leave untouched
    } catch (Exception e) {
      throw new OperatorException("Couldn't register event source: " + name + " for "
          + controller.getConfiguration().getName() + " controller`", e);
    } finally {
      lock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
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
    return eventSources.flatMappedSources()
        .map(NamedEventSource::original)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public ControllerResourceEventSource<R> getControllerResourceEventSource() {
    return eventSources.controllerResourceEventSource;
  }

  public <S> Optional<ResourceEventSource<R, S>> getResourceEventSourceFor(
      Class<S> dependentType) {
    return getResourceEventSourceFor(dependentType, null);
  }

  public <S> Optional<ResourceEventSource<R, S>> getResourceEventSourceFor(
      Class<S> dependentType, String qualifier) {
    if (dependentType == null) {
      return Optional.empty();
    }
    String name = qualifier == null ? "" : qualifier;
    final var eventSource = eventSources.get(dependentType, name);
    return Optional.ofNullable(eventSource);
  }

  TimerEventSource<R> retryEventSource() {
    return eventSources.retryAndRescheduleTimerEventSource;
  }

  Controller<R> getController() {
    return controller;
  }

  static class NamedEventSource implements EventSource {
    private final EventSource original;
    private final String name;

    private NamedEventSource(EventSource original, String name) {
      this.original = original;
      this.name = name;
    }

    @Override
    public void start() throws OperatorException {
      original.start();
    }

    @Override
    public void stop() throws OperatorException {
      original.stop();
    }

    @Override
    public void setEventHandler(EventHandler handler) {
      original.setEventHandler(handler);
    }

    public String name() {
      return name;
    }

    @Override
    public String toString() {
      return original + " named: '" + name + "'}";
    }

    public EventSource original() {
      return original;
    }
  }

  private static class EventSources<R extends HasMetadata> implements Iterable<NamedEventSource> {
    private final ConcurrentNavigableMap<String, Map<String, EventSource>> sources =
        new ConcurrentSkipListMap<>();
    private final TimerEventSource<R> retryAndRescheduleTimerEventSource = new TimerEventSource<>();
    private ControllerResourceEventSource<R> controllerResourceEventSource;


    ControllerResourceEventSource<R> initControllerEventSource(Controller<R> controller) {
      controllerResourceEventSource = new ControllerResourceEventSource<>(controller);
      return controllerResourceEventSource;
    }

    TimerEventSource<R> retryEventSource() {
      return retryAndRescheduleTimerEventSource;
    }

    @Override
    public Iterator<NamedEventSource> iterator() {
      return flatMappedSources().iterator();
    }

    private Stream<NamedEventSource> flatMappedSources() {
      return sources.values().stream().flatMap(c -> c.entrySet().stream()
          .map(esEntry -> new NamedEventSource(esEntry.getValue(), esEntry.getKey())));
    }

    public void clear() {
      sources.clear();
    }

    public boolean contains(String name, EventSource source) {
      final var eventSources = sources.get(keyFor(source));
      if (eventSources == null || eventSources.isEmpty()) {
        return false;
      }
      return eventSources.containsKey(name);
    }

    public void add(String name, EventSource eventSource) {
      if (contains(name, eventSource)) {
        throw new IllegalArgumentException("An event source is already registered for the "
            + keyAsString(getDependentType(eventSource), name)
            + " class/name combination");
      }
      sources.computeIfAbsent(keyFor(eventSource), k -> new HashMap<>()).put(name, eventSource);
    }

    @SuppressWarnings("rawtypes")
    private Class<?> getDependentType(EventSource source) {
      return source instanceof ResourceEventSource
          ? ((ResourceEventSource) source).getResourceClass()
          : source.getClass();
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

    @SuppressWarnings("unchecked")
    public <S> ResourceEventSource<R, S> get(Class<S> dependentType, String name) {
      final var sourcesForType = sources.get(keyFor(dependentType));
      if (sourcesForType == null || sourcesForType.isEmpty()) {
        return null;
      }

      final var size = sourcesForType.size();
      final EventSource source;
      if (size == 1) {
        source = sourcesForType.values().stream().findFirst().orElse(null);
      } else {
        if (name == null || name.isBlank()) {
          throw new IllegalArgumentException("There are multiple EventSources registered for type "
              + dependentType.getCanonicalName()
              + ", you need to provide a name to specify which EventSource you want to query. Known names: "
              + String.join(",", sourcesForType.keySet()));
        }
        source = sourcesForType.get(name);

        if (source == null) {
          return null;
        }
      }

      if (!(source instanceof ResourceEventSource)) {
        throw new IllegalArgumentException(source + " associated with "
            + keyAsString(dependentType, name) + " is not a "
            + ResourceEventSource.class.getSimpleName());
      }
      final var res = (ResourceEventSource<R, S>) source;
      final var resourceClass = res.getResourceClass();
      if (!resourceClass.isAssignableFrom(dependentType)) {
        throw new IllegalArgumentException(source + " associated with "
            + keyAsString(dependentType, name)
            + " is handling " + resourceClass.getName() + " resources but asked for "
            + dependentType.getName());
      }
      return res;
    }

    @SuppressWarnings("rawtypes")
    private String keyAsString(Class dependentType, String name) {
      return name != null && name.length() > 0
          ? "(" + dependentType.getName() + ", " + name + ")"
          : dependentType.getName();
    }
  }
}
