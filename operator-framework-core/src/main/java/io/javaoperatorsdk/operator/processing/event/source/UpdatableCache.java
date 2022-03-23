package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public interface UpdatableCache<T> extends Cache<T> {
  T remove(ObjectKey key);

  void put(ObjectKey key, T resource);
}
