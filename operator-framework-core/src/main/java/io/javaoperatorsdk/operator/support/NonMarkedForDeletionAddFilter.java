package io.javaoperatorsdk.operator.support;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;

public class NonMarkedForDeletionAddFilter<T extends HasMetadata> implements OnAddFilter<T> {
  @Override
  public boolean accept(T resource) {
    return resource.getMetadata().getDeletionTimestamp() != null;
  }

}
