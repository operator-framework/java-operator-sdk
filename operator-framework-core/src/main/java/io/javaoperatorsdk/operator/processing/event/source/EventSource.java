package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

public interface EventSource<T extends EventSourceConfiguration> extends LifecycleAware {
  @SuppressWarnings("unchecked")
  default T getConfiguration() {
    return (T) (EventSourceConfiguration) () -> EventSource.this.getClass().getCanonicalName();
  }

  void setEventHandler(EventHandler handler);
}
