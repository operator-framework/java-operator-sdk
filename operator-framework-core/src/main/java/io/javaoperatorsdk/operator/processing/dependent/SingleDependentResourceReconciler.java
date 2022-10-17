package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

class SingleDependentResourceReconciler<R, P extends HasMetadata>
    implements DependentResourceReconciler<R, P> {

  private final AbstractDependentResource<R, P> instance;

  SingleDependentResourceReconciler(AbstractDependentResource<R, P> dependentResource) {
    this.instance = dependentResource;
  }

  @Override
  public ReconcileResult<R> reconcile(P primary, Context<P> context) {
    final var maybeActual = instance.getSecondaryResource(primary, context);
    return instance.reconcile(primary, maybeActual.orElse(null), context);
  }

  @Override
  public void delete(P primary, Context<P> context) {
    var secondary = instance.getSecondaryResource(primary, context);
    instance.handleDelete(primary, secondary.orElse(null), context);
  }
}
