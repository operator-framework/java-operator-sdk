package io.javaoperatorsdk.operator.processing.event;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;

public interface EventSourceRetriever<P extends HasMetadata> {

  default <R> ResourceEventSource<R, P> getResourceEventSourceFor(Class<R> dependentType) {
    return getResourceEventSourceFor(dependentType, null);
  }

  <R> ResourceEventSource<R, P> getResourceEventSourceFor(Class<R> dependentType, String name);

  <R> List<ResourceEventSource<R, P>> getResourceEventSourcesFor(Class<R> dependentType);

  /**
   * The event source for primary resource. Note that this event source is exposed only to cover
   * some corner cases, mainly to access caches regarding the primary resource; what is not needed
   * in standard use cases.
   */
  ControllerResourceEventSource<P> controllerResourceEventSource();
}
