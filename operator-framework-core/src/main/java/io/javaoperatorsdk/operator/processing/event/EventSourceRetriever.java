package io.javaoperatorsdk.operator.processing.event;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public interface EventSourceRetriever<P extends HasMetadata> {

  default <R> ResourceEventSource<R, P> getResourceEventSourceFor(Class<R> dependentType) {
    return getResourceEventSourceFor(dependentType, null);
  }

  <R> ResourceEventSource<R, P> getResourceEventSourceFor(Class<R> dependentType, String name);

  <R> List<ResourceEventSource<R, P>> getResourceEventSourcesFor(Class<R> dependentType);

  // todo javadocs
  // this will be an idempotent synchronized operation
  void dynamicallyRegisterEventSource(String name, EventSource eventSource);

  void dynamicallyDeRegisterEventSource(String name);

  EventSourceContext<P> eventSourceContexForDynamicRegistration();

}
