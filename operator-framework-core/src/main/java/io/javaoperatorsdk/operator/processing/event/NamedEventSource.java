package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;
import java.util.Optional;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.processing.event.source.Configurable;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

class NamedEventSource implements EventSource, EventSourceMetadata {

  private final EventSource original;
  private final String name;
  private final boolean nameSet;

  NamedEventSource(EventSource original, String name) {
    this.original = original;
    this.name = name;
    nameSet = !name.equals(EventSourceInitializer.generateNameFor(original));
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
  public Class<?> type() {
    return original.getClass();
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public Optional<Class<?>> resourceType() {
    if (original instanceof ResourceEventSource) {
      ResourceEventSource resourceEventSource = (ResourceEventSource) original;
      return Optional.of(resourceEventSource.resourceType());
    }
    return Optional.empty();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Optional<?> configuration() {
    if (original instanceof Configurable) {
      Configurable configurable = (Configurable) original;
      return Optional.ofNullable(configurable.configuration());
    }
    return Optional.empty();
  }

  public EventSource eventSource() {
    return original;
  }

  @Override
  public String toString() {
    return original + " named: '" + name;
  }

  public EventSource original() {
    return original;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NamedEventSource that = (NamedEventSource) o;
    return Objects.equals(original, that.original) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(original, name);
  }

  @Override
  public EventSourceStartPriority priority() {
    return original.priority();
  }

  public boolean isNameSet() {
    return nameSet;
  }
}
