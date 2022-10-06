package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

interface DependentResourceReconciler<R, P extends HasMetadata> {

  ReconcileResult<R> reconcile(P primary, Context<P> context);

  void delete(P primary, Context<P> context);
}
