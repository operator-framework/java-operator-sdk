package io.javaoperatorsdk.operator.processing.dependent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

class BulkDependentResourceReconciler<R, P extends HasMetadata>
    implements DependentResourceReconciler<R, P> {

  private final BulkDependentResource<R, P> bulkDependentResource;

  BulkDependentResourceReconciler(BulkDependentResource<R, P> bulkDependentResource) {
    this.bulkDependentResource = bulkDependentResource;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    final var desiredResources = bulkDependentResource.desiredResources(primary, context);
    Map<String, R> actualResources = bulkDependentResource.getSecondaryResources(primary, context);

    // remove existing resources that are not needed anymore according to the primary state
    deleteExtraResources(desiredResources.keySet(), actualResources, primary, context);

    final List<ReconcileResult<R>> results = new ArrayList<>(desiredResources.size());
    final var updatable = bulkDependentResource instanceof Updater;
    desiredResources.forEach((key, value) -> {
      final var instance =
          updatable ? new UpdatableBulkDependentResourceInstance<>(bulkDependentResource, value)
              : new BulkDependentResourceInstance<>(bulkDependentResource, value);
      results.add(instance.reconcile(primary, actualResources.get(key), context));
    });

    return ReconcileResult.aggregatedResult(results);
  }

  @Override
  public void delete(P primary, Context<P> context) {
    var actualResources = bulkDependentResource.getSecondaryResources(primary, context);
    deleteExtraResources(Collections.emptySet(), actualResources, primary, context);
  }

  private void deleteExtraResources(Set<String> expectedKeys,
      Map<String, R> actualResources, P primary, Context<P> context) {
    actualResources.forEach((key, value) -> {
      if (!expectedKeys.contains(key)) {
        bulkDependentResource.deleteTargetResource(primary, value, key, context);
      }
    });
  }

  /**
   * Exposes a dynamically-created instance of the bulk dependent resource precursor as an
   * AbstractDependentResource so that we can reuse its reconciliation logic.
   *
   * @param <R>
   * @param <P>
   */
  private static class BulkDependentResourceInstance<R, P extends HasMetadata>
      extends AbstractDependentResource<R, P>
      implements Creator<R, P>, Deleter<P> {
    private final BulkDependentResource<R, P> bulkDependentResource;
    private final R desired;

    private BulkDependentResourceInstance(BulkDependentResource<R, P> bulkDependentResource,
        R desired) {
      this.bulkDependentResource = bulkDependentResource;
      this.desired = desired;
    }

    @SuppressWarnings("unchecked")
    private AbstractDependentResource<R, P> asAbstractDependentResource() {
      return (AbstractDependentResource<R, P>) bulkDependentResource;
    }

    @Override
    protected R desired(P primary, Context<P> context) {
      return desired;
    }

    @SuppressWarnings("unchecked")
    public R update(R actual, R desired, P primary, Context<P> context) {
      return ((Updater<R, P>) bulkDependentResource).update(actual, desired, primary, context);
    }

    @Override
    public Result<R> match(R resource, P primary, Context<P> context) {
      return bulkDependentResource.match(resource, desired, primary, context);
    }

    @Override
    protected void onCreated(P primary, R created, Context<P> context) {
      asAbstractDependentResource().onCreated(primary, created, context);
    }

    @Override
    protected void onUpdated(P primary, R updated, R actual, Context<P> context) {
      asAbstractDependentResource().onUpdated(primary, updated, actual, context);
    }

    @Override
    public Class<R> resourceType() {
      return asAbstractDependentResource().resourceType();
    }

    @Override
    public R create(R desired, P primary, Context<P> context) {
      return bulkDependentResource.create(desired, primary, context);
    }
  }

  /**
   * Makes sure that the instance implements Updater if its precursor does as well.
   *
   * @param <R>
   * @param <P>
   */
  private static class UpdatableBulkDependentResourceInstance<R, P extends HasMetadata>
      extends BulkDependentResourceInstance<R, P> implements Updater<R, P> {

    private UpdatableBulkDependentResourceInstance(
        BulkDependentResource<R, P> bulkDependentResource,
        R desired) {
      super(bulkDependentResource, desired);
    }
  }
}
