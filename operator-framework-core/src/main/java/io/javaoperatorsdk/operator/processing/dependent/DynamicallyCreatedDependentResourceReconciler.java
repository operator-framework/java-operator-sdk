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

class DynamicallyCreatedDependentResourceReconciler<R, P extends HasMetadata>
    implements DependentResourceReconciler<R, P> {

  private final DynamicallyCreatedDependentResource<R, P> delegate;

  DynamicallyCreatedDependentResourceReconciler(
      DynamicallyCreatedDependentResource<R, P> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    final var desiredResources = delegate.desiredResources(primary, context);
    Map<String, R> actualResources = delegate.getSecondaryResources(primary, context);

    // remove existing resources that are not needed anymore according to the primary state
    deleteUnexpectedDynamicResources(desiredResources.keySet(), actualResources, primary, context);

    final List<ReconcileResult<R>> results = new ArrayList<>(desiredResources.size());
    final var updatable = delegate instanceof Updater;
    desiredResources.forEach((key, value) -> {
      final var instance = updatable ? new UpdatableDynamicDependentInstance<>(delegate, value)
          : new DynamicDependentInstance<>(delegate, value);
      results.add(instance.reconcile(primary, actualResources.get(key), context));
    });

    return ReconcileResult.aggregatedResult(results);
  }

  @Override
  public void delete(P primary, Context<P> context) {
    var actualResources =
        delegate.getSecondaryResources(primary, context);
    deleteUnexpectedDynamicResources(Collections.emptySet(), actualResources, primary, context);
  }

  private void deleteUnexpectedDynamicResources(Set<String> expectedKeys,
      Map<String, R> actualResources, P primary, Context<P> context) {
    actualResources.forEach((key, value) -> {
      if (!expectedKeys.contains(key)) {
        delegate.deleteTargetResource(primary, value, context);
      }
    });
  }

  /**
   * Exposes a dynamically-created instance of the dynamically-created dependent resource precursor
   * as an AbstractDependentResource so that we can reuse its reconciliation logic.
   *
   * @param <R>
   * @param <P>
   */
  private static class DynamicDependentInstance<R, P extends HasMetadata>
      extends AbstractDependentResource<R, P>
      implements Creator<R, P>, Deleter<P> {
    private final DynamicallyCreatedDependentResource<R, P> delegate;
    private final R desired;

    private DynamicDependentInstance(DynamicallyCreatedDependentResource<R, P> delegate,
        R desired) {
      this.delegate = delegate;
      this.desired = desired;
    }

    @SuppressWarnings("unchecked")
    private AbstractDependentResource<R, P> asAbstractDependentResource() {
      return (AbstractDependentResource<R, P>) delegate;
    }

    @Override
    protected R desired(P primary, Context<P> context) {
      return desired;
    }

    @SuppressWarnings("unchecked")
    public R update(R actual, R desired, P primary, Context<P> context) {
      return ((Updater<R, P>) delegate).update(actual, desired, primary,
          context);
    }

    @Override
    public Result<R> match(R resource, P primary, Context<P> context) {
      return delegate.match(resource, desired, primary, context);
    }

    @Override
    protected void onCreated(ResourceID primaryResourceId, R created) {
      asAbstractDependentResource().onCreated(primaryResourceId, created);
    }

    @Override
    protected void onUpdated(ResourceID primaryResourceId, R updated, R actual) {
      asAbstractDependentResource().onUpdated(primaryResourceId, updated, actual);
    }

    @Override
    public Class<R> resourceType() {
      return asAbstractDependentResource().resourceType();
    }

    @Override
    public R create(R desired, P primary, Context<P> context) {
      return delegate.create(desired, primary, context);
    }
  }

  /**
   * Makes sure that the instance implements Updater if its precursor does as well.
   *
   * @param <R>
   * @param <P>
   */
  private static class UpdatableDynamicDependentInstance<R, P extends HasMetadata>
      extends DynamicDependentInstance<R, P> implements Updater<R, P> {

    private UpdatableDynamicDependentInstance(DynamicallyCreatedDependentResource<R, P> delegate,
        R desired) {
      super(delegate, desired);
    }
  }
}
