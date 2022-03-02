package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ConcurrentHashMapCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

/** A base class for external dependent resources which don't have an event source. */
public class AbstractExternalDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  private final UpdatableCache<R> cache = new ConcurrentHashMapCache<>();

  // todo do we always want this? this should be just in case it's not a reconciliation
  @Override
  public Optional<R> getResource(HasMetadata primaryResource) {
    return cache.get(ResourceID.fromResource(primaryResource));
  }

  public void delete(P primary, Context context) {
    super.delete(primary, context);
    cache.remove(ResourceID.fromResource(primary));
  }

  @Override
  protected R handleCreate(R desired, P primary, Context context) {
    var res = this.creator.create(desired, primary, context);
    cache.put(ResourceID.fromResource(primary), res);
    return res;
  }

  @Override
  protected R handleUpdate(R actual, R desired, P primary, Context context) {
    var res = updater.update(actual, desired, primary, context);
    cache.put(ResourceID.fromResource(primary), res);
    return res;
  }
}
