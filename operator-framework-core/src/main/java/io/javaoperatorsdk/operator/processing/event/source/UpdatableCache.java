package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface UpdatableCache<T> extends Cache<T> {
  T remove(ResourceID key);

  void put(ResourceID key, T resource);
}
