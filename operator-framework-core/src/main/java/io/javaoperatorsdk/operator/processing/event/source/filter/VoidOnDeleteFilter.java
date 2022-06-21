package io.javaoperatorsdk.operator.processing.event.source.filter;

import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidOnDeleteFilter implements BiPredicate<HasMetadata, Boolean> {
  @Override
  public boolean test(HasMetadata hasMetadata, Boolean aBoolean) {
    return true;
  }
}
