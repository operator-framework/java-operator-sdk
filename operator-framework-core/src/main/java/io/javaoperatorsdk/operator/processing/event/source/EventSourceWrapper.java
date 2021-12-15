package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public interface EventSourceWrapper<T extends HasMetadata>
    extends LifecycleAware, ResourceCache<T> {
}
