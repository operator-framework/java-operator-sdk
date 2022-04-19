package io.javaoperatorsdk.operator.api.reconciler.dependent;

/**
 * Marker interface, that can be added in additionally to a {@link Deleter} Dependent Resource. It
 * is used to model a situation when a resource is makes sure to delete during reconciliation, but
 * not during cleanup, since that is handled by the Kubernetes garbage collector. See also:
 * https://github.com/java-operator-sdk/java-operator-sdk/issues/1127
 */
public interface GarbageCollected {
}
