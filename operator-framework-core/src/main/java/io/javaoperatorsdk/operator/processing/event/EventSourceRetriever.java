package io.javaoperatorsdk.operator.processing.event;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public interface EventSourceRetriever<P extends HasMetadata> {

  <R> ResourceEventSource<R, P> getResourceEventSourceFor(
      Class<R> dependentType);

  <R> ResourceEventSource<R, P> getResourceEventSourceFor(
      Class<R> dependentType, String qualifier);

  <R> List<ResourceEventSource<R, P>> getResourceEventSourcesFor(Class<R> dependentType);

}
