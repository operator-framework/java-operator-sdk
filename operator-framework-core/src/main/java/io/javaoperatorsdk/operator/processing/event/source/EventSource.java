package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

public interface EventSource extends LifecycleAware {
  default String name() {
    return getClass().getCanonicalName();
  }

  void setEventHandler(EventHandler handler);
}
