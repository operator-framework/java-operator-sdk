package io.javaoperatorsdk.operator.support;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class NonMarkedForDeletionUpdateFilter<T extends HasMetadata> implements OnUpdateFilter<T> {

  @Override
  public boolean accept(T newResource, T oldResource) {
    return newResource.getMetadata().getDeletionTimestamp() != null;
  }
}
