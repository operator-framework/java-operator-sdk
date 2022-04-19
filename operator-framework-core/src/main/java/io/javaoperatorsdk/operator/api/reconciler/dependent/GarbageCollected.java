package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * Used to describe the situation when a resource is makes sure to delete during reconciliation, but
 * not during cleanup, since that is handled by the Kubernetes garbage collector. See also:
 * https://github.com/java-operator-sdk/java-operator-sdk/issues/1127
 */
public interface GarbageCollected<P extends HasMetadata> extends Deleter<P> {
}
