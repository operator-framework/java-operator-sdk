package io.javaoperatorsdk.operator.processing;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface ResourceCache<T extends HasMetadata> {

  Optional<T> getCustomResource(ResourceID resourceID);

  default Stream<T> getCachedCustomResources() {
    return getCachedCustomResources(a -> true);
  }

  Stream<T> getCachedCustomResources(Predicate<T> predicate);
}
