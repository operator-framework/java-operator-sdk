package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ObjectKey;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface Cache<T> {
  Predicate TRUE = (a) -> true;

  Optional<T> get(ObjectKey objectKey);

  default boolean contains(ObjectKey objectKey) {
    return get(objectKey).isPresent();
  }

  Stream<ObjectKey> keys();

  default Stream<T> list() {
    return list(TRUE);
  }

  Stream<T> list(Predicate<T> predicate);
}
