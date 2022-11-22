package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;
import java.util.Optional;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;

class NamedEventSource implements EventSource, EventSourceMetadata {

  private final EventSource original;
  private final String name;
  private final AssociatedDependentMetadata dependentMetadata;

  NamedEventSource(EventSource original, String name) {
    this(original, name, null);
  }

  NamedEventSource(EventSource original, String name,
      AssociatedDependentMetadata dependentMetadata) {
    this.original = original;
    this.name = name;
    this.dependentMetadata = dependentMetadata;
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
  public EventSource eventSource() {
    return original;
  }

  @Override
  public Optional<AssociatedDependentMetadata> associatedDependentClassNameIfExists() {
    return Optional.ofNullable(dependentMetadata);
  }

  @Override
  public String toString() {
    return original + " named: '" + name + "'}";
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
}
