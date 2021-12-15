package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface ResourceCache<T extends HasMetadata> {
  Predicate TRUE = (a) -> true;

  Optional<T> get(ResourceID resourceID);

  default Stream<T> list() {
    return list(TRUE);
  }

  Stream<T> list(Predicate<T> predicate);

  default Stream<T> list(String namespace) {
    return list(namespace, TRUE);
  }

  Stream<T> list(String namespace, Predicate<T> predicate);
}
