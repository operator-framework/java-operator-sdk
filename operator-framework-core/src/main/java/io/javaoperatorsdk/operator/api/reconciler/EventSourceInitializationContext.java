package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

public class EventSourceInitializationContext<P extends HasMetadata> {

  private final ResourceCache<P> primaryCache;

  public EventSourceInitializationContext(ResourceCache<P> primaryCache) {
    this.primaryCache = primaryCache;
  }

  public ResourceCache<P> getPrimaryCache() {
    return primaryCache;
  }
}
