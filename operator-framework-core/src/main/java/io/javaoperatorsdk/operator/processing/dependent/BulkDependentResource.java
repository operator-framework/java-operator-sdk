package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

// todo event source provider to default interface (with Optional result) , without that this does
// not work?
public abstract class BulkDependentResource<R, P extends HasMetadata>
    implements DependentResource<R, P> {

  private BulkResourceDiscriminatorFactory<R, P> bulkResourceDiscriminatorFactory;
  private AbstractDependentResource<R, P> dependentResource;

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    var count = count(primary, context);
    for (int i = 0; i < count; i++) {
      var res = reconcileSingleDependent(primary, i, context);
    }
    return null;
  }

  private ReconcileResult<R> reconcileSingleDependent(P primary, int i, Context<P> context) {
    if (dependentResource instanceof Creator || dependentResource instanceof Updater) {
      dependentResource.setDesired(desired(primary, i, context));
      // todo cache those discriminators
      dependentResource.setResourceDiscriminator(
          bulkResourceDiscriminatorFactory.createResourceDiscriminator(i));
    }
    return dependentResource.reconcile(primary, context);
  }

  protected R desired(P primary, int index, Context<P> context) {
    throw new IllegalStateException(
        "Needs to be implemented if dependent resource is creator or updater");
  }

  protected abstract int count(P primary, Context<P> context);
}
