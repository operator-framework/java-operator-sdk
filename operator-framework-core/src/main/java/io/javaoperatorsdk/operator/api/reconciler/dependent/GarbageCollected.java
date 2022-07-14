package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * <p>
 * Can be implemented by a dependent resource extending
 * {@link io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource}
 * to express that the resource deletion is handled by the controller during
 * {@link DependentResource#reconcile(HasMetadata, Context)}. This takes effect during a
 * reconciliation workflow, but not during a cleanup workflow, when a {@code reconcilePrecondition}
 * is not met for the resource or a resource on which the the dependent that implements this
 * interface depends on is not ready ({@code readyPostCondition} not met). In this case,
 * {@link #delete(HasMetadata, Context)} is called. During a cleanup workflow, however,
 * {@link #delete(HasMetadata, Context)} is not called, letting the Kubernetes garbage collector do
 * its work instead (using owner references).
 * </p>
 * <p>
 * If a dependent resource implement this interface, an owner reference pointing to the associated
 * primary resource will be automatically added to this managed resource.
 * </p>
 * <p>
 * See <a href="https://github.com/java-operator-sdk/java-operator-sdk/issues/1127">this issue</a>
 * for more details.
 * </p>
 */
public interface GarbageCollected<P extends HasMetadata> extends Deleter<P> {
}
