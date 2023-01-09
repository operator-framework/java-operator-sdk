package io.javaoperatorsdk.operator.processing.cache;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface Cache<R> {
  Predicate TRUE = (a) -> true;

  Optional<R> get(ResourceID resourceID);

  default boolean contains(ResourceID resourceID) {
    return get(resourceID).isPresent();
  }

  // todo remove?
  @Deprecated(forRemoval = true)
  Stream<ResourceID> keys();

  default Stream<R> list() {
    return list(TRUE);
  }

  Stream<R> list(Predicate<R> predicate);
}
