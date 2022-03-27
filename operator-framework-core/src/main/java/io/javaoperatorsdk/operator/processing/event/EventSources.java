package io.javaoperatorsdk.operator.processing.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

class EventSources<R extends HasMetadata> implements Iterable<NamedEventSource> {

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

  @Override
  public Iterator<NamedEventSource> iterator() {
    return flatMappedSources().iterator();
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
