package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ConcurrentHashMapCache;

public class AbstractExternalDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  private final ConcurrentHashMapCache<R> cache = new ConcurrentHashMapCache<>();

  @Override
  public Optional<R> getResource(HasMetadata primaryResource) {
    return cache.get(ResourceID.fromResource(primaryResource));
  }

  public void delete(P primary, Context context) {
    super.delete(primary, context);
    cache.remove(ResourceID.fromResource(primary));
  }
}
