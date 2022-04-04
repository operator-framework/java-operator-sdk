package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@FunctionalInterface
public interface PrimaryToSecondaryMapper<P extends HasMetadata> {
  ResourceID toSecondaryResourceID(P primary);
}
