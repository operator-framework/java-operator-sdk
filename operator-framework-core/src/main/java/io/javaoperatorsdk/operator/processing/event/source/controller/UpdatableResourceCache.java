package io.javaoperatorsdk.operator.processing.event.source.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface UpdatableResourceCache<T extends HasMetadata> extends ResourceCache<T> {
  T remove(ResourceID key);

  void put(ResourceID key, T resource);
}
