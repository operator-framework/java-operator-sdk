package io.javaoperatorsdk.operator.processing.event.source.filter;

import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class VoidGenericFilter implements Predicate<HasMetadata> {
  @Override
  public boolean test(HasMetadata hasMetadata) {
    return true;
  }
}
