package io.javaoperatorsdk.operator.processing.event.source.filter;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidGenericFilter implements GenericFilter<HasMetadata> {
  @Override
  public boolean accept(HasMetadata hasMetadata) {
    return true;
  }
}
