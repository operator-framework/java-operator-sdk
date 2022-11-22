package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public interface EventSourceMetadata {
  String name();

  EventSource eventSource();

  class AssociatedDependentMetadata {
    public final String name;
    public final String className;

    public AssociatedDependentMetadata(String name, String className) {
      this.name = name;
      this.className = className;
    }
  }

  @SuppressWarnings("unused")
  default Optional<AssociatedDependentMetadata> associatedDependentClassNameIfExists() {
    return Optional.empty();
  }
}
