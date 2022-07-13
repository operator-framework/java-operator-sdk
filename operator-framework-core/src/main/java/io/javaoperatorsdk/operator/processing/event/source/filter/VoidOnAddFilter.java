package io.javaoperatorsdk.operator.processing.event.source.filter;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidOnAddFilter implements OnAddFilter<HasMetadata> {
  @Override
  public boolean accept(HasMetadata hasMetadata) {
    return true;
  }
}
