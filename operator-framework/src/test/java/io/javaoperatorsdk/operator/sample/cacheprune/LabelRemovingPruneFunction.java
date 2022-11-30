package io.javaoperatorsdk.operator.sample.cacheprune;

import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class LabelRemovingPruneFunction<R extends HasMetadata> implements UnaryOperator<R> {
  @Override
  public R apply(R r) {
    r.getMetadata().setLabels(null);
    return r;
  }
}
