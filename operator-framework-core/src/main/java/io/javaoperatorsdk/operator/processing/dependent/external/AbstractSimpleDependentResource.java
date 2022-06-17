package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.DesiredEqualsMatcher;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ConcurrentHashMapCache;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

/** A base class for external dependent resources that don't have an event source. */
@Ignore
public abstract class AbstractSimpleDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  // cache serves only to keep the resource readable again until next reconciliation when the
  // new resource is read again.
  protected final UpdatableCache<R> cache;
  protected Matcher<R, P> matcher;

  public AbstractSimpleDependentResource() {
    this(new ConcurrentHashMapCache<>());
  }

  public AbstractSimpleDependentResource(UpdatableCache<R> cache) {
    this.cache = cache;
    initMatcher();
  }

  @Override
  public Optional<R> getSecondaryResource(HasMetadata primaryResource) {
    return cache.get(ResourceID.fromResource(primaryResource));
  }

  /**
   * Actually read the resource from the target API
   *
   * @param primaryResource the primary associated resource
   * @return fetched resource if present
   **/
  public abstract Optional<R> fetchResource(HasMetadata primaryResource);

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    var resourceId = ResourceID.fromResource(primary);
    Optional<R> resource = fetchResource(primary);
    resource.ifPresentOrElse(r -> cache.put(resourceId, r), () -> cache.remove(resourceId));
    return super.reconcile(primary, context);
  }

  public final void delete(P primary, Context<P> context) {
    deleteResource(primary, context);
    cache.remove(ResourceID.fromResource(primary));
  }

  protected abstract void deleteResource(P primary, Context<P> context);

  @Override
  protected void onCreated(ResourceID primaryResourceId, R created) {
    cache.put(primaryResourceId, created);
  }

  @Override
  protected void onUpdated(ResourceID primaryResourceId, R updated, R actual) {
    cache.put(primaryResourceId, updated);
  }

  public Matcher.Result<R> match(R actualResource, P primary, Context<P> context) {
    return matcher.match(actualResource, primary, context);
  }

  protected void initMatcher() {
    matcher = new DesiredEqualsMatcher<>(this);
  }

}
