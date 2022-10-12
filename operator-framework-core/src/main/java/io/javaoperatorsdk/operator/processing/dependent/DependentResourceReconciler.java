package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

/**
 * An internal interface that abstracts away how to reconcile dependent resources, in particular
 * when they can be dynamically created based on the state provided by the primary resource (e.g.
 * the primary resource dictates which/how many secondary resources need to be created).
 *
 * @param <R> the type of the secondary resource to be reconciled
 * @param <P> the primary resource type
 */
interface DependentResourceReconciler<R, P extends HasMetadata> {

  ReconcileResult<R> reconcile(P primary, Context<P> context);

  void delete(P primary, Context<P> context);
}
