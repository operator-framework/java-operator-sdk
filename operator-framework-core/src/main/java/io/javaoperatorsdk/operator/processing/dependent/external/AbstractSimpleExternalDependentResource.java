package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ConcurrentHashMapCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

/** A base class for external dependent resources that don't have an event source. */
public abstract class AbstractSimpleExternalDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  // cache serves only to keep the resource readable again until next reconciliation when the
  // new resource is read again.
  protected final UpdatableCache<R> cache;

  public AbstractSimpleExternalDependentResource() {
    this(new ConcurrentHashMapCache<>());
  }

  public AbstractSimpleExternalDependentResource(UpdatableCache<R> cache) {
    this.cache = cache;
  }

  @Override
  public Optional<R> getResource(HasMetadata primaryResource) {
    return cache.get(ResourceID.fromResource(primaryResource));
  }

  /** Actually read the resource from the target API */
  public abstract Optional<R> supplyResource(HasMetadata primaryResource);

  @Override
  public void reconcile(P primary, Context context) {
    var resourceId = ResourceID.fromResource(primary);
    Optional<R> resource = supplyResource(primary);
    resource.ifPresentOrElse(r -> cache.put(resourceId, r), () -> cache.remove(resourceId));
    super.reconcile(primary, context);
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
