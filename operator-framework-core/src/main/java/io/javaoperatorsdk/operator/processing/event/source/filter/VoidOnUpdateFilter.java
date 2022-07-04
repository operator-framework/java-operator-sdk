package io.javaoperatorsdk.operator.processing.event.source.filter;

import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidOnUpdateFilter implements BiPredicate<HasMetadata, HasMetadata> {
  @Override
  public boolean test(HasMetadata hasMetadata, HasMetadata hasMetadata2) {
    return true;
  }
}
