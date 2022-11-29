package io.javaoperatorsdk.operator.sample.cacheprune;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.informer.ObjectTransformingItemStore;

public class LabelRemovingItemStore<R extends HasMetadata> extends ObjectTransformingItemStore<R> {

  public LabelRemovingItemStore() {
    super(r -> {
      r.getMetadata().setLabels(null);
      return r;
    });
  }
}
