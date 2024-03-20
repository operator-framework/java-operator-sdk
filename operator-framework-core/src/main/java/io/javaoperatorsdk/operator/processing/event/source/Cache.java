package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface Cache<T> {
  Predicate TRUE = (a) -> true;

  Optional<T> get(ResourceID resourceID);

  default boolean contains(ResourceID resourceID) {
    return get(resourceID).isPresent();
  }

  Stream<ResourceID> keys();

  default Stream<T> list() {
    return list(TRUE);
  }

  Stream<T> list(Predicate<T> predicate);
}
