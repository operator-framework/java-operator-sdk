package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

@FunctionalInterface
public interface SecondaryToPrimaryMapper<R> {
  Set<ResourceID> toPrimaryResourceIDs(R secondaryResource);
}
