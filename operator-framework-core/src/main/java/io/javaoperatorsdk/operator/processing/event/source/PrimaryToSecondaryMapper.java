package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ObjectKey;

@FunctionalInterface
public interface PrimaryToSecondaryMapper<P extends HasMetadata> {
  ObjectKey associatedSecondaryID(P primary);
}
