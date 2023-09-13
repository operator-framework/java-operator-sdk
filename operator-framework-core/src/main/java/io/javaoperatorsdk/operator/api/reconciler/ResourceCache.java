package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.Cache;

@SuppressWarnings("unchecked")
public interface ResourceCache<T extends HasMetadata> extends Cache<T> {

  default Stream<T> list(String namespace) {
    return list(namespace, TRUE);
  }

  Stream<T> list(String namespace, Predicate<T> predicate);
}
