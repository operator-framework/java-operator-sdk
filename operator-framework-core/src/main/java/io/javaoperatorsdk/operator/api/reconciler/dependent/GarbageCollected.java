package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * Should be implemented by {@link DependentResource} implementations that are explicitly deleted
 * during reconciliation but which should also benefit from Kubernetes' automated garbage collection
 * during the cleanup phase.
 * <p>
 * See <a href="https://github.com/java-operator-sdk/java-operator-sdk/issues/1127">this issue</a>
 * for more details.
 */
public interface GarbageCollected<P extends HasMetadata> extends Deleter<P> {

}
