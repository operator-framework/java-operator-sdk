package io.javaoperatorsdk.operator.processing.event.source.pool;

public interface EventSourcePool<C, T> {

  T getEventSource(C classifier);

  void removeEventSource(T informerEventSource);
}
