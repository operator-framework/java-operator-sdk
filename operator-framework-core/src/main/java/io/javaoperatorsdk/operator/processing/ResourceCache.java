package io.javaoperatorsdk.operator.processing;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface ResourceCache<T extends HasMetadata> {

  Optional<T> get(ResourceID resourceID);

  default Stream<T> list() {
    return list(a -> true);
  }

  Stream<T> list(Predicate<T> predicate);
}
