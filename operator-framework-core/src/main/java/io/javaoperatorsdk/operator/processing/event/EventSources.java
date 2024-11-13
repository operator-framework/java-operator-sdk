package io.javaoperatorsdk.operator.processing.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

class EventSources<R extends HasMetadata> {

  private final ConcurrentNavigableMap<String, Map<String, EventSource>> sources =
      new ConcurrentSkipListMap<>();
  private final TimerEventSource<R> retryAndRescheduleTimerEventSource =
      new TimerEventSource<>("RetryAndRescheduleTimerEventSource");
  private ControllerResourceEventSource<R> controllerResourceEventSource;


  void createControllerEventSource(Controller<R> controller) {
    controllerResourceEventSource = new ControllerResourceEventSource<>(controller);
  }

  ControllerResourceEventSource<R> controllerResourceEventSource() {
    return controllerResourceEventSource;
  }

  TimerEventSource<R> retryEventSource() {
    return retryAndRescheduleTimerEventSource;
  }

  public Stream<EventSource> allEventSources() {
    return Stream.concat(
        Stream.of(controllerResourceEventSource(), retryAndRescheduleTimerEventSource),
        flatMappedSources());
  }

  Stream<EventSource> additionalEventSources() {
    return Stream.concat(
        Stream.of(retryEventSource()).filter(Objects::nonNull),
        flatMappedSources());
  }

  Stream<EventSource> flatMappedSources() {
    return sources.values().stream().flatMap(c -> c.values().stream());
  }

  public void clear() {
    sources.clear();
  }

  public EventSource existingEventSourceOfSameNameAndType(EventSource source) {
    return existingEventSourceOfSameType(source).get(source.name());
  }

  public Map<String, EventSource> existingEventSourceOfSameType(EventSource source) {
    return sources.getOrDefault(keyFor(source), Collections.emptyMap());
  }

  public void add(EventSource eventSource) {
    final var name = eventSource.name();
    final var existing = existingEventSourceOfSameType(eventSource);
    if (existing.get(name) != null) {
      throw new IllegalArgumentException("Event source " + existing
          + " is already registered with name: " + name);
    }

    sources.computeIfAbsent(keyFor(eventSource), k -> new HashMap<>()).put(name, eventSource);
  }

  @SuppressWarnings("rawtypes")
  private Class<?> getResourceType(EventSource source) {
    return source instanceof ResourceEventSource
        ? ((ResourceEventSource) source).resourceType()
        : source.getClass();
  }

  private String keyFor(EventSource source) {
    return keyFor(getResourceType(source));
  }

  private String keyFor(Class<?> dependentType) {
    return dependentType.getCanonicalName();
  }

  @SuppressWarnings("unchecked")
  public <S> ResourceEventSource<S, R> get(Class<S> dependentType, String name) {
    if (dependentType == null) {
      throw new IllegalArgumentException("Must pass a dependent type to retrieve event sources");
    }

    final var sourcesForType = sources.get(keyFor(dependentType));
    if (sourcesForType == null || sourcesForType.isEmpty()) {
      throw new IllegalArgumentException(
          "There is no event source found for class:" + dependentType.getName());
    }

    final var size = sourcesForType.size();
    EventSource source;
    if (size == 1 && name == null) {
      source = sourcesForType.values().stream().findFirst().orElseThrow();
    } else {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("There are multiple EventSources registered for type "
            + dependentType.getCanonicalName()
            + ", you need to provide a name to specify which EventSource you want to query. Known names: "
            + String.join(",", sourcesForType.keySet()));
      }
      source = sourcesForType.get(name);

      if (source == null) {
        throw new IllegalArgumentException("There is no event source found for class:" +
            " " + dependentType.getName() + ", name:" + name);
      }
    }

    if (!(source instanceof ResourceEventSource)) {
      throw new IllegalArgumentException(source + " associated with "
          + keyAsString(dependentType, name) + " is not a "
          + ResourceEventSource.class.getSimpleName());
    }
    final var res = (ResourceEventSource<S, R>) source;
    final var resourceClass = res.resourceType();
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
    return name != null && !name.isEmpty()
        ? "(" + dependentType.getName() + ", " + name + ")"
        : dependentType.getName();
  }

  @SuppressWarnings("unchecked")
  public <S> List<ResourceEventSource<S, R>> getEventSources(Class<S> dependentType) {
    final var sourcesForType = sources.get(keyFor(dependentType));
    if (sourcesForType == null) {
      return Collections.emptyList();
    }

    return sourcesForType.values().stream()
        .filter(ResourceEventSource.class::isInstance)
        .map(es -> (ResourceEventSource<S, R>) es)
        .collect(Collectors.toList());
  }

  public EventSource remove(String name) {
    var optionalMap = sources.values().stream().filter(m -> m.containsKey(name)).findFirst();
    return optionalMap.map(m -> m.remove(name)).orElse(null);
  }
}
