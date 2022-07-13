package io.javaoperatorsdk.operator.processing.event.source.filter;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidOnDeleteFilter implements OnDeleteFilter<HasMetadata> {
  @Override
  public boolean accept(HasMetadata hasMetadata, Boolean aBoolean) {
    return true;
  }
}
