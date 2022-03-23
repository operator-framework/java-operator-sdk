package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ObjectKey;

@FunctionalInterface
public interface SecondaryToPrimaryMapper<T> {
  Set<ObjectKey> associatedPrimaryResources(T dependentResource);
}
