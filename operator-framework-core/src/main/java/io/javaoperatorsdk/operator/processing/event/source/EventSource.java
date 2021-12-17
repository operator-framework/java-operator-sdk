package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.LifecycleAware;

public interface EventSource<P extends HasMetadata> extends LifecycleAware {

  void setEventSourceRegistry(EventSourceRegistry<P> registry);

  EventSourceRegistry<P> getEventSourceRegistry();
}
