package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ResourceIDMatcherDiscriminator<R extends HasMetadata, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {

  private final Function<P, ResourceID> mapper;

  public ResourceIDMatcherDiscriminator(Function<P, ResourceID> mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<R> distinguish(Class<R> resource, P primary, Context<P> context) {
    var resourceID = mapper.apply(primary);
    return context.getSecondaryResources(resource).stream()
        .filter(r -> r.getMetadata().getName()
            .equals(resourceID.getName()) &&
            resourceID.getNamespace().map(ns -> ns.equals(r.getMetadata().getNamespace()))
                .orElse(true))
        .findFirst();
  }
}
