package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

// todo resource: resource which we don't manage just aware of, and needs it for input
//
public interface ObservedResource<R, P extends HasMetadata> {

  default Optional<EventSource> initEventSource(EventSourceContext<P> context) {
    return Optional.empty();
  }

  Optional<R> getResource();

}
