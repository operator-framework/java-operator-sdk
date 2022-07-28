package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

// todo this requires rather a matcher (name+namespace) as input
public abstract class ResourceListDiscriminator<R, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {
  @Override
  public Optional<R> distinguish(Class<R> resource, P primary, Context<P> context,
      EventSourceRetriever<P> eventSourceManager) {
    var resources = context.getSecondaryResources(resource);
    return distinguish(primary, resources);
  }

  protected abstract Optional<R> distinguish(P primary, Set<R> resourceList);
}
