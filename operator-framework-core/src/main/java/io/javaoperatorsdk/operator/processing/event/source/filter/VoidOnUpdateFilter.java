package io.javaoperatorsdk.operator.processing.event.source.filter;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidOnUpdateFilter implements OnUpdateFilter<HasMetadata> {
  @Override
  public boolean accept(HasMetadata hasMetadata, HasMetadata hasMetadata2) {
    return true;
  }
}
