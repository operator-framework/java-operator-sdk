package io.javaoperatorsdk.operator.processing.event;

import java.util.*;
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

  public static final String CONTROLLER_RESOURCE_EVENT_SOURCE_NAME =
      "ControllerResourceEventSource";
  public static final String RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME =
      "RetryAndRescheduleTimerEventSource";

  private final ConcurrentNavigableMap<String, Map<String, EventSource>> sources =
      new ConcurrentSkipListMap<>();
  private final TimerEventSource<R> retryAndRescheduleTimerEventSource = new TimerEventSource<>();
  private ControllerResourceEventSource<R> controllerResourceEventSource;


  ControllerResourceEventSource<R> initControllerEventSource(Controller<R> controller) {
    controllerResourceEventSource = new ControllerResourceEventSource<>(controller);
    return controllerResourceEventSource;
  }

  ControllerResourceEventSource<R> controllerResourceEventSource() {
    return controllerResourceEventSource;
  }

  TimerEventSource<R> retryEventSource() {
    return retryAndRescheduleTimerEventSource;
  }

  public Stream<NamedEventSource> additionalNamedEventSources() {
    return Stream.concat(Stream.of(
        new NamedEventSource(retryAndRescheduleTimerEventSource,
            RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME)),
        flatMappedSources());
  }

  public Stream<NamedEventSource> allNamedEventSources() {
    return Stream.concat(Stream.of(namedControllerResourceEventSource(),
        new NamedEventSource(retryAndRescheduleTimerEventSource,
            RETRY_RESCHEDULE_TIMER_EVENT_SOURCE_NAME)),
        flatMappedSources());
  }

  Stream<EventSource> additionalEventSources() {
    return Stream.concat(
        Stream.of(retryEventSource()).filter(Objects::nonNull),
        sources.values().stream().flatMap(c -> c.values().stream()));
  }

  NamedEventSource namedControllerResourceEventSource() {
    return new NamedEventSource(controllerResourceEventSource,
        CONTROLLER_RESOURCE_EVENT_SOURCE_NAME);
  }

  Stream<NamedEventSource> flatMappedSources() {
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
          + keyAsString(getResourceType(eventSource), name)
          + " class/name combination");
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
    final var sourcesForType = sources.get(keyFor(dependentType));
    if (sourcesForType == null || sourcesForType.isEmpty()) {
      throw new IllegalArgumentException(
          "There is no event source found for class:" + dependentType.getName());
    }

    final var size = sourcesForType.size();
    final EventSource source;
    if (size == 1 && name == null) {
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
    return name != null && name.length() > 0
        ? "(" + dependentType.getName() + ", " + name + ")"
        : dependentType.getName();
  }

  @SuppressWarnings("unchecked")
  public <S> List<ResourceEventSource<S, R>> getEventSources(Class<S> dependentType) {
    final var sourcesForType = sources.get(keyFor(dependentType));
    return sourcesForType.values().stream()
        .filter(ResourceEventSource.class::isInstance)
        .map(es -> (ResourceEventSource<S, R>) es)
        .collect(Collectors.toList());
  }
}
