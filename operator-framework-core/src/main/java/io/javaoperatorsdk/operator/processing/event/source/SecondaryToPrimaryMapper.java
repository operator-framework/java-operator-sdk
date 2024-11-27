package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@FunctionalInterface
public interface SecondaryToPrimaryMapper<R> {

  default void setPrimaryResourceType(Class<? extends HasMetadata> primaryResourceType) {};

  Set<ResourceID> toPrimaryResourceIDs(R resource);
}
