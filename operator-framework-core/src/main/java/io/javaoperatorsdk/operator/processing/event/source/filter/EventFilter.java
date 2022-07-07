package io.javaoperatorsdk.operator.processing.event.source.filter;

public interface EventFilter<R> {
  @SuppressWarnings("rawtypes")
  EventFilter ACCEPTS_ALL = new EventFilter() {};

  default boolean acceptsAdding(R resource) {
    return true;
  }

  default boolean acceptsUpdating(R from, R to) {
    return true;
  }

  default boolean acceptsDeleting(R resource) {
    return true;
  }

  default boolean rejects(R resource) {
    return false;
  }
}
