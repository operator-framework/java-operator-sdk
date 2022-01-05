package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ResourceEventSource<P extends HasMetadata, R> extends EventSource {

  Class<R> getResourceClass();

  Optional<R> getAssociated(P primary);
}
