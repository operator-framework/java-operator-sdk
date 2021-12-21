package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

@FunctionalInterface
public interface PrimaryResourcesRetriever<T> {

  Set<ResourceID> associatedPrimaryResources(T dependentResource);
}
