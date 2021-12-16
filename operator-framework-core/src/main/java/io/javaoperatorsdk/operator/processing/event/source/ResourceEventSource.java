package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ResourceEventSource<P extends HasMetadata, R> extends EventSource<P> {

  Class<R> getResourceClass();

  R getAssociated(P primary);
}
