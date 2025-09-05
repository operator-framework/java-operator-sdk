package io.javaoperatorsdk.operator.processing.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

class EventSources<P extends HasMetadata> {

  private final ConcurrentNavigableMap<String, Map<String, EventSource<?, P>>> sources =
      new ConcurrentSkipListMap<>();
  private final Map<String, EventSource> sourceByName = new HashMap<>();

  private final TimerEventSource<P> retryAndRescheduleTimerEventSource;
  private ControllerEventSource<P> controllerEventSource;

  public EventSources(boolean triggerReconcilerOnAllEvent) {
    retryAndRescheduleTimerEventSource =
        new TimerEventSource<>("RetryAndRescheduleTimerEventSource", triggerReconcilerOnAllEvent);
  }

  EventSources() {
    this(false);
  }

  public void add(EventSource eventSource) {
    final var name = eventSource.name();
    var existing = sourceByName.get(name);
    if (existing != null) {
      throw new IllegalArgumentException(
          "Event source " + existing + " is already registered with name: " + name);
    }
    sourceByName.put(name, eventSource);
    sources
        .computeIfAbsent(keyFor(eventSource), k -> new ConcurrentHashMap<>())
        .put(name, eventSource);
  }

  public EventSource remove(String name) {
    var optionalMap = sources.values().stream().filter(m -> m.containsKey(name)).findFirst();
    sourceByName.remove(name);
    return optionalMap.map(m -> m.remove(name)).orElse(null);
  }

  public void clear() {
    sources.clear();
    sourceByName.clear();
  }

  public EventSource existingEventSourceByName(String name) {
    return sourceByName.get(name);
  }

  void createControllerEventSource(Controller<P> controller) {
    controllerEventSource = new ControllerEventSource<>(controller);
  }

  public ControllerEventSource<P> controllerEventSource() {
    return controllerEventSource;
  }

  TimerEventSource<P> retryEventSource() {
    return retryAndRescheduleTimerEventSource;
  }

  @SuppressWarnings("rawtypes")
  public Stream<EventSource> allEventSources() {
    return Stream.concat(
        Stream.of(controllerEventSource(), retryAndRescheduleTimerEventSource),
        flatMappedSources());
  }

  @SuppressWarnings("rawtypes")
  Stream<EventSource> additionalEventSources() {
    return Stream.concat(
        Stream.of(retryEventSource()).filter(Objects::nonNull), flatMappedSources());
  }

  Stream<EventSource<?, P>> flatMappedSources() {
    return sources.values().stream().flatMap(c -> c.values().stream());
  }

  private <R> String keyFor(EventSource<R, P> source) {
    return keyFor(source.resourceType());
  }

  private String keyFor(Class<?> dependentType) {
    return dependentType.getCanonicalName();
  }

  @SuppressWarnings("unchecked")
  public <S> EventSource<S, P> get(Class<S> dependentType, String name) {
    if (dependentType == null) {
      throw new IllegalArgumentException("Must pass a dependent type to retrieve event sources");
    }

    final var sourcesForType = sources.get(keyFor(dependentType));
    if (sourcesForType == null || sourcesForType.isEmpty()) {
      throw new NoEventSourceForClassException(dependentType);
    }

    final var size = sourcesForType.size();
    EventSource<S, P> source;
    if (size == 1 && name == null) {
      source = (EventSource<S, P>) sourcesForType.values().stream().findFirst().orElseThrow();
    } else {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException(
            "There are multiple EventSources registered for type "
                + dependentType.getCanonicalName()
                + ", you need to provide a name to specify which EventSource you want to query."
                + " Known names: "
                + String.join(",", sourcesForType.keySet()));
      }
      source = (EventSource<S, P>) sourcesForType.get(name);

      if (source == null) {
        throw new IllegalArgumentException(
            "There is no event source found for class:"
                + " "
                + dependentType.getName()
                + ", name:"
                + name);
      }
    }

    final var resourceClass = source.resourceType();
    if (!resourceClass.isAssignableFrom(dependentType)) {
      throw new IllegalArgumentException(
          source
              + " associated with "
              + keyAsString(dependentType, name)
              + " is handling "
              + resourceClass.getName()
              + " resources but asked for "
              + dependentType.getName());
    }
    return source;
  }

  @SuppressWarnings("rawtypes")
  private String keyAsString(Class dependentType, String name) {
    return name != null && !name.isEmpty()
        ? "(" + dependentType.getName() + ", " + name + ")"
        : dependentType.getName();
  }

  @SuppressWarnings("unchecked")
  public <S> List<EventSource<S, P>> getEventSources(Class<S> dependentType) {
    final var sourcesForType = sources.get(keyFor(dependentType));
    if (sourcesForType == null) {
      return Collections.emptyList();
    }
    return sourcesForType.values().stream().map(es -> (EventSource<S, P>) es).toList();
  }
}
