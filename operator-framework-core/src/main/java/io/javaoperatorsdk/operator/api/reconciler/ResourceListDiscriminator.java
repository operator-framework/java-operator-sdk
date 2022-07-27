package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public abstract class ResourceListDiscriminator<R, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {
  @Override
  public Optional<R> distinguish(Class<R> resource, Context<P> context,
      EventSourceRetriever<P> eventSourceManager) {
    var resources = context.getSecondaryResources(resource);
    return distinguish(resources);
  }

  abstract Optional<R> distinguish(Set<R> resourceList);
}
