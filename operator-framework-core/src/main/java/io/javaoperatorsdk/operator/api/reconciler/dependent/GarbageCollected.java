package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * <p>
 * Can be implemented by a dependent resource extending
 * {@link io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource}
 * to express that the resource deletion is handled by the controller during "reconcile". So in a
 * workflow during "reconcile" (not during cleanup) when a "reconcilePrecondition" is not met for
 * the resource delete is called. During the cleanup however rather than calling explicitly delete
 * Kubernetes garbage collector should clean up the resource (using owner references).
 * </p>
 * <p>
 * If a dependent resource implement this interface an owner reference is automatically added to the
 * managed resource.
 * </p>
 * <p>
 * See <a href="https://github.com/java-operator-sdk/java-operator-sdk/issues/1127">this issue</a>
 * for more details.
 * </p>
 */
public interface GarbageCollected<P extends HasMetadata> extends Deleter<P> {
}
