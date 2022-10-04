package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public interface DeferrableEventSourceHolder<P extends HasMetadata> {

  default void useEventSourceWithName(String name) {}

  Optional<String> resolveEventSource(EventSourceRetriever<P> eventSourceRetriever);

}
