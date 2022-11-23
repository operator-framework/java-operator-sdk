package io.javaoperatorsdk.operator.processing.event;

import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public interface EventSourceMetadata {
  String name();

  EventSource eventSource();
}
