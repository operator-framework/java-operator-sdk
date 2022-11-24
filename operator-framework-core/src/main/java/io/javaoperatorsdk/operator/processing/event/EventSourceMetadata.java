package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

public interface EventSourceMetadata {
  String name();

  Class<?> type();

  Optional<Class<?>> resourceType();

  Optional<?> configuration();
}
