package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public interface DependentResource<R, P extends HasMetadata> {
  default EventSource initEventSource(EventSourceContext<P> context) {
    throw new IllegalStateException("Must be implemented if not automatically provided by the SDK");
  };

  default Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromInterface(getClass());
  }
}
