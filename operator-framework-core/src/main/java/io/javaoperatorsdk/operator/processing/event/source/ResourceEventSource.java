package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.MultiResourceOwner;

public interface ResourceEventSource<R, P extends HasMetadata> extends EventSource,
    MultiResourceOwner<R, P> {

}
